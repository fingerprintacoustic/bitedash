import { getAuth } from "firebase-admin/auth";
import { Firestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions/v2";
import * as nodemailer from "nodemailer";

/**
 * Email alerts for new orders, until the app has push notifications.
 *
 * Who hears about what:
 *  - Cash on Delivery: the restaurant and the admins, as soon as it's placed.
 *  - Manual Pay: the admins when it's placed (a payment to check), then the
 *    restaurant once an admin confirms the money arrived.
 *  - Paynow: the restaurant and the admins once it's paid.
 * The restaurant is never told about an order it can't see yet, so nobody cooks
 * an unpaid order.
 *
 * "The restaurant" is its owner's account email plus its staffEmails. The three
 * admin-run restaurants have no owner, which is why admins hear about every order.
 *
 * The customer also gets one confirmation email per order: when it's placed for
 * Cash on Delivery and Manual Pay (saying the payment is being checked), and once
 * it's paid for Paynow, so nobody is told an unpaid order went through.
 */

export type AlertKind = "new_cash" | "payment_to_confirm" | "paid";

export interface MailCredentials {
  user: string;
  appPassword: string;
}

interface OrderLine {
  name?: string;
  price?: number;
  quantity?: number;
}

interface OrderData {
  userId?: string;
  restaurantId?: string;
  restaurantName?: string;
  customerName?: string;
  customerPhone?: string;
  customerAddress?: string;
  itemsSummary?: string;
  items?: OrderLine[];
  subtotal?: number;
  deliveryFee?: number;
  driverTip?: number;
  totalCost?: number;
  paymentMethod?: string;
  paymentStatus?: string;
  customerPaymentReference?: string;
}

/** Which alert (if any) an order that was just created needs. */
export function alertForNewOrder(order: OrderData): AlertKind | null {
  if (order.paymentStatus === "CASH_ON_DELIVERY") return "new_cash";
  if (order.paymentStatus === "AWAITING_MANUAL_CONFIRMATION") return "payment_to_confirm";
  return null; // Paynow: wait until it's paid.
}

/** Which alert (if any) an order update needs: only the moment it becomes paid. */
export function alertForUpdate(before: OrderData, after: OrderData): AlertKind | null {
  if (after.paymentStatus === "PAID" && before.paymentStatus !== "PAID") return "paid";
  return null;
}

/**
 * Whether the customer's confirmation is due: when Cash on Delivery or Manual Pay
 * orders are placed, or when a Paynow order is paid. A Manual Pay order turning
 * PAID later finds its confirmation already sent (one claim per order).
 */
export function customerConfirmationDue(order: OrderData, justPaid: boolean): boolean {
  if (justPaid) return true;
  return order.paymentStatus === "CASH_ON_DELIVERY" || order.paymentStatus === "AWAITING_MANUAL_CONFIRMATION";
}

const isEmail = (s: unknown): s is string => typeof s === "string" && /^[^\s@]+@[^\s@]+$/.test(s.trim());

async function adminEmails(db: Firestore): Promise<string[]> {
  const snap = await db.collection("users").where("role", "==", "admin").get();
  return snap.docs.map((d) => d.get("email")).filter(isEmail);
}

async function restaurantEmails(db: Firestore, restaurantId: string | undefined): Promise<string[]> {
  if (!restaurantId) return [];
  const restaurant = await db.collection("restaurants").doc(restaurantId).get();
  if (!restaurant.exists) return [];
  const emails: string[] = [];
  const ownerUserId = restaurant.get("ownerUserId");
  if (typeof ownerUserId === "string" && ownerUserId) {
    const owner = await db.collection("users").doc(ownerUserId).get();
    if (isEmail(owner.get("email"))) emails.push(owner.get("email"));
  }
  const staff = restaurant.get("staffEmails");
  if (Array.isArray(staff)) emails.push(...staff.filter(isEmail));
  return emails;
}

const PAYMENT_LABELS: Record<string, string> = {
  CASH_ON_DELIVERY: "Cash on Delivery",
  ECO_CASH: "EcoCash",
  ONE_MONEY: "OneMoney",
  INNBUCKS: "InnBucks",
  TELECASH: "Telecash",
  OMARI: "O'Mari",
};

function composeEmail(kind: AlertKind, orderId: string, order: OrderData, forRestaurant: boolean) {
  const restaurant = (order.restaurantName || "").trim() || "a restaurant";
  const total = typeof order.totalCost === "number" ? `$${order.totalCost.toFixed(2)}` : "";
  const method = PAYMENT_LABELS[order.paymentMethod || ""] || order.paymentMethod || "";

  let subject: string;
  let intro: string;
  if (kind === "payment_to_confirm") {
    subject = `Payment to confirm: ${total} ${method} order for ${restaurant}`;
    intro = "A customer says they've sent this payment. Check it arrived, then open BiteDash → Admin Control Hub → " +
      "Manual Pay and tap Confirm Received (or Not Received). The restaurant only sees the order after that.";
  } else if (forRestaurant) {
    subject = `New BiteDash order: ${total} (${method})`;
    intro = kind === "new_cash"
      ? "You have a new order. The customer pays cash on delivery. Open BiteDash to accept it."
      : "You have a new paid order. Open BiteDash to accept it.";
  } else {
    subject = `New order for ${restaurant}: ${total} (${method})`;
    intro = kind === "new_cash"
      ? "A new Cash on Delivery order was placed. The restaurant has been emailed too, if it has an owner or staff on file."
      : "An order has been paid and is now waiting for the restaurant. The restaurant has been emailed too, if it has an owner or staff on file.";
  }

  const lines = [
    intro,
    "",
    `Restaurant: ${restaurant}`,
    `Items: ${order.itemsSummary || ""}`,
    `Total: ${total}`,
    `Payment: ${method}`,
  ];
  if (kind === "payment_to_confirm" && order.customerPaymentReference) {
    lines.push(`Customer's payment reference: ${order.customerPaymentReference}`);
  }
  lines.push(
    `Customer: ${order.customerName || ""}`,
    `Phone: ${order.customerPhone || ""}`,
    `Delivery address: ${order.customerAddress || ""}`,
    `Order ID: ${orderId}`,
    "",
    "This is an automatic message from BiteDash.",
  );
  return { subject, text: lines.join("\n") };
}

const money = (n: unknown) => (typeof n === "number" && Number.isFinite(n) ? `$${n.toFixed(2)}` : "");

function composeCustomerEmail(orderId: string, order: OrderData) {
  const restaurant = (order.restaurantName || "").trim() || "the restaurant";
  const method = PAYMENT_LABELS[order.paymentMethod || ""] || order.paymentMethod || "";
  const name = (order.customerName || "").trim();

  const items = Array.isArray(order.items) && order.items.length
    ? order.items.map((i) => {
      const qty = typeof i.quantity === "number" ? i.quantity : 1;
      const lineTotal = typeof i.price === "number" ? ` - ${money(i.price * qty)}` : "";
      return `  ${qty} x ${i.name || "Item"}${lineTotal}`;
    })
    : [`  ${order.itemsSummary || ""}`];

  let paymentNote: string;
  if (order.paymentStatus === "AWAITING_MANUAL_CONFIRMATION") {
    paymentNote = `We're checking your ${method} payment. Once it's confirmed, ${restaurant} will start on your order.`;
  } else if (order.paymentStatus === "CASH_ON_DELIVERY") {
    paymentNote = "Please have cash ready to pay on delivery.";
  } else {
    paymentNote = `Your ${method} payment has been received.`;
  }

  const lines = [
    name ? `Hi ${name},` : "Hi,",
    "",
    `Thanks for your order from ${restaurant}.`,
    "",
    "Your order:",
    ...items,
    "",
  ];
  if (typeof order.subtotal === "number") lines.push(`Subtotal: ${money(order.subtotal)}`);
  if (typeof order.deliveryFee === "number" && order.deliveryFee > 0) lines.push(`Delivery: ${money(order.deliveryFee)}`);
  if (typeof order.driverTip === "number" && order.driverTip > 0) lines.push(`Driver tip: ${money(order.driverTip)}`);
  lines.push(
    `Total: ${money(order.totalCost)}`,
    `Payment: ${method}`,
    "",
    paymentNote,
    "You'll be notified in the BiteDash app as your order progresses.",
    "",
    `Order ID: ${orderId}`,
    "This is an automatic message from BiteDash.",
  );
  return { subject: `Your BiteDash order from ${restaurant}`, text: lines.join("\n") };
}

async function customerEmail(db: Firestore, userId: string | undefined): Promise<string | null> {
  if (!userId) return null;
  const user = await db.collection("users").doc(userId).get();
  const email = user.get("email");
  if (isEmail(email)) return email.trim();
  try {
    const authEmail = (await getAuth().getUser(userId)).email;
    return isEmail(authEmail) ? authEmail.trim() : null;
  } catch {
    return null;
  }
}

/** Sends the customer's order confirmation once per order (claimed as {orderId}_customer). */
export async function sendCustomerConfirmation(
  db: Firestore,
  creds: MailCredentials,
  orderId: string,
  order: OrderData
): Promise<void> {
  if (!creds.user || !creds.appPassword) {
    logger.warn("Customer confirmation: Gmail address or app password not set, skipping", { orderId });
    return;
  }
  const claim = db.collection("order_alerts").doc(`${orderId}_customer`);
  try {
    await claim.create({ orderId, kind: "customer", createdAt: new Date() });
  } catch {
    logger.info("Customer confirmation already sent", { orderId });
    return;
  }

  try {
    const to = await customerEmail(db, order.userId);
    if (!to) {
      await claim.update({ skipped: "no customer email", sentAt: new Date() });
      logger.warn("Customer confirmation: no email on the customer's account", { orderId });
      return;
    }
    const transport = nodemailer.createTransport({
      service: "gmail",
      auth: { user: creds.user, pass: creds.appPassword },
    });
    await transport.sendMail({ from: `BiteDash Orders <${creds.user}>`, to, ...composeCustomerEmail(orderId, order) });
    await claim.update({ sentAt: new Date() });
    logger.info("Customer confirmation sent", { orderId });
  } catch (e) {
    await claim.delete().catch(() => undefined);
    logger.error("Customer confirmation failed", { orderId, error: String(e) });
    throw e;
  }
}

/**
 * Sends the alert for [kind] once. Functions can deliver an event more than once,
 * so each (order, kind) is claimed in order_alerts first; a repeat finds it taken
 * and does nothing. If sending fails, the claim is removed so a retry can send it.
 */
export async function sendOrderAlert(
  db: Firestore,
  creds: MailCredentials,
  kind: AlertKind,
  orderId: string,
  order: OrderData
): Promise<void> {
  if (!creds.user || !creds.appPassword) {
    logger.warn("Order alerts: Gmail address or app password not set, skipping", { orderId, kind });
    return;
  }
  const claim = db.collection("order_alerts").doc(`${orderId}_${kind}`);
  try {
    await claim.create({ orderId, kind, createdAt: new Date() });
  } catch {
    logger.info("Order alert already sent", { orderId, kind });
    return;
  }

  try {
    const admins = await adminEmails(db);
    const restaurant = kind === "payment_to_confirm" ? [] : await restaurantEmails(db, order.restaurantId);
    // Someone who is both (e.g. an admin who is also staff) gets the restaurant's version only.
    const adminOnly = admins.filter((a) => !restaurant.some((r) => r.toLowerCase() === a.toLowerCase()));

    const transport = nodemailer.createTransport({
      service: "gmail",
      auth: { user: creds.user, pass: creds.appPassword },
    });
    const from = `BiteDash Orders <${creds.user}>`;
    const sends: Promise<unknown>[] = [];
    if (restaurant.length) {
      const mail = composeEmail(kind, orderId, order, true);
      sends.push(transport.sendMail({ from, to: from, bcc: restaurant, ...mail }));
    }
    if (adminOnly.length) {
      const mail = composeEmail(kind, orderId, order, false);
      sends.push(transport.sendMail({ from, to: from, bcc: adminOnly, ...mail }));
    }
    await Promise.all(sends);
    await claim.update({ restaurantCount: restaurant.length, adminCount: adminOnly.length, sentAt: new Date() });
    logger.info("Order alert sent", { orderId, kind, restaurantCount: restaurant.length, adminCount: adminOnly.length });
  } catch (e) {
    await claim.delete().catch(() => undefined);
    logger.error("Order alert failed", { orderId, kind, error: String(e) });
    throw e; // retried only if retries are on; otherwise this log is the record
  }
}
