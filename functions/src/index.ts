import { initializeApp } from "firebase-admin/app";
import { getFirestore, FieldValue, Firestore } from "firebase-admin/firestore";
import { onCall, onRequest, HttpsError } from "firebase-functions/v2/https";
import { defineSecret } from "firebase-functions/params";
import { logger } from "firebase-functions/v2";
import {
  initiateTransaction,
  initiateExpressTransaction,
  normaliseZimbabweanMobile,
  pollTransactionStatus,
  validatePaynowHash,
  ExpressMethod,
  PaynowPaymentStatus,
} from "./paynow";

initializeApp();
const db = getFirestore();

const REGION = "us-central1";
const PROJECT_ID = process.env.GCLOUD_PROJECT;
const FUNCTIONS_HOST = `https://${REGION}-${PROJECT_ID}.cloudfunctions.net`;
const RESULT_URL = `${FUNCTIONS_HOST}/paynowResultWebhook`;
const RETURN_URL = `${FUNCTIONS_HOST}/paynowReturn`;

const PAYNOW_INTEGRATION_ID = defineSecret("PAYNOW_INTEGRATION_ID");
const PAYNOW_INTEGRATION_KEY = defineSecret("PAYNOW_INTEGRATION_KEY");

// Express checkout needs the payer's email. While the Paynow integration is in TEST
// mode, Paynow only accepts an email that belongs to the merchant account, so
// PAYNOW_AUTH_EMAIL_OVERRIDE (set in functions/.env) can hold that email for testing.
// Leave it unset in live mode: the customer's own email is used. Not a secret.
const paynowAuthEmailOverride = (): string => (process.env.PAYNOW_AUTH_EMAIL_OVERRIDE || "").trim();

// App payment-method values that are paid by approving a prompt on the customer's phone.
const EXPRESS_METHODS: Record<string, ExpressMethod> = {
  ECO_CASH: "ecocash",
  ONE_MONEY: "onemoney",
  INNBUCKS: "innbucks",
};

const PAYMENTS_COLLECTION = "payments";
const ORDERS_COLLECTION = "orders";
const AMOUNT_TOLERANCE = 0.02;

/**
 * Idempotently apply a verified Paynow status to a payment + its order.
 * Runs in a transaction so a poll response and the resulturl webhook racing
 * each other can't double-apply or clobber a later state with an earlier one.
 */
async function applyVerifiedStatus(
  firestore: Firestore,
  paymentId: string,
  status: PaynowPaymentStatus,
  paidAmount: number,
  paynowReference: string
): Promise<{ status: PaynowPaymentStatus; errorMessage?: string }> {
  const paymentRef = firestore.collection(PAYMENTS_COLLECTION).doc(paymentId);

  return firestore.runTransaction(async (tx) => {
    const snap = await tx.get(paymentRef);
    if (!snap.exists) {
      logger.warn(`applyVerifiedStatus: payment ${paymentId} not found`);
      return { status: "FAILED", errorMessage: "Payment record not found" };
    }

    const payment = snap.data() as {
      status: string;
      orderId: string;
      amount: number;
      method?: string;
    };

    // Already terminal — don't let a stale/duplicate callback overwrite it.
    if (payment.status === "PAID" || payment.status === "FAILED" || payment.status === "CANCELLED") {
      return { status: payment.status as PaynowPaymentStatus };
    }

    let finalStatus: PaynowPaymentStatus = status;
    let errorMessage: string | undefined;

    if (status === "PAID" && Math.abs(paidAmount - payment.amount) > AMOUNT_TOLERANCE) {
      finalStatus = "FAILED";
      errorMessage = `Amount mismatch: expected ${payment.amount}, Paynow reported ${paidAmount}`;
      logger.error(`Payment ${paymentId}: ${errorMessage}`);
    }

    const paymentUpdate: Record<string, unknown> = {
      status: finalStatus,
      updatedAt: FieldValue.serverTimestamp(),
    };
    if (finalStatus === "PAID") paymentUpdate.completedAt = FieldValue.serverTimestamp();
    if (errorMessage) paymentUpdate.errorMessage = errorMessage;
    tx.update(paymentRef, paymentUpdate);

    if (finalStatus === "PAID" && payment.orderId) {
      const orderRef = firestore.collection(ORDERS_COLLECTION).doc(payment.orderId);
      tx.update(orderRef, {
        paymentStatus: "PAID",
        paymentMethod: payment.method || "",
        paymentRef: paynowReference,
        updatedAt: FieldValue.serverTimestamp(),
      });
    }

    return { status: finalStatus, errorMessage };
  });
}

/**
 * Initiate a Paynow payment for an order the caller owns. The Paynow
 * Integration ID/Key never leave this function — the app only ever sees a
 * transactionId + browserUrl to open.
 */
export const initiatePaynowPayment = onCall(
  { secrets: [PAYNOW_INTEGRATION_ID, PAYNOW_INTEGRATION_KEY], region: REGION },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Sign in required");
    }
    const uid = request.auth.uid;
    const orderId = String(request.data?.orderId || "");
    const method = String(request.data?.method || "");
    const mobileMoneyNumber = String(request.data?.mobileMoneyNumber || "");

    if (!orderId) {
      throw new HttpsError("invalid-argument", "orderId is required");
    }

    const orderRef = db.collection(ORDERS_COLLECTION).doc(orderId);
    const orderSnap = await orderRef.get();
    if (!orderSnap.exists) {
      throw new HttpsError("not-found", "Order not found");
    }
    const order = orderSnap.data() as { userId?: string; totalCost?: number; paymentStatus?: string };
    if (order.userId !== uid) {
      throw new HttpsError("permission-denied", "This order does not belong to you");
    }
    if (order.paymentStatus === "PAID") {
      throw new HttpsError("failed-precondition", "This order has already been paid");
    }

    // Amount always comes from the order document, never the client — this
    // is what stops a tampered client request from paying a different
    // amount than the order actually costs.
    const amount = order.totalCost || 0;
    if (amount <= 0) {
      throw new HttpsError("failed-precondition", "Order has no payable amount");
    }

    const reference = `BD_${orderId.replace(/[^a-zA-Z0-9]/g, "").slice(0, 20)}_${Date.now()}`;

    // Mobile money paid on the customer's own phone (Paynow express checkout), so they
    // never leave the app. Only when the app asks for it: an older app build that doesn't
    // send `express` still gets the hosted Paynow page it knows how to open.
    if (request.data?.express === true) {
      const expressMethod = EXPRESS_METHODS[method];
      if (!expressMethod) {
        throw new HttpsError("invalid-argument", "This payment method is paid on the Paynow page, not on your phone");
      }
      const phone = normaliseZimbabweanMobile(mobileMoneyNumber);
      if (!phone) {
        throw new HttpsError("invalid-argument", "Enter a valid Zimbabwean mobile number, for example 0771234567");
      }
      const authEmail =
        paynowAuthEmailOverride() ||
        String(request.auth.token.email || "") ||
        `customer+${uid}@example.com`;

      const express = await initiateExpressTransaction({
        integrationId: PAYNOW_INTEGRATION_ID.value().trim(),
        integrationKey: PAYNOW_INTEGRATION_KEY.value().trim(),
        reference,
        amount,
        additionalInfo: `BiteDash Order ${orderId}`,
        returnUrl: RETURN_URL,
        resultUrl: RESULT_URL,
        authEmail,
        phone,
        method: expressMethod,
      });

      if (!express.ok || !express.pollUrl) {
        logger.error(`initiatePaynowPayment (express ${expressMethod}) failed for order ${orderId}: ${express.error}`);
        throw new HttpsError("internal", express.error || "Paynow rejected the payment request");
      }

      const expressPayment = await db.collection(PAYMENTS_COLLECTION).add({
        userId: uid,
        orderId,
        amount,
        currency: "USD",
        status: "PENDING",
        method,
        mode: "express",
        mobileMoneyNumber: phone,
        pollUrl: express.pollUrl,
        paynowReference: reference,
        createdAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      });

      return {
        transactionId: expressPayment.id,
        pollUrl: express.pollUrl,
        instructions: express.instructions || "",
        authorizationCode: express.authorizationCode || "",
        authorizationExpires: express.authorizationExpires || "",
      };
    }

    const result = await initiateTransaction({
      integrationId: PAYNOW_INTEGRATION_ID.value().trim(),
      integrationKey: PAYNOW_INTEGRATION_KEY.value().trim(),
      reference,
      amount,
      additionalInfo: `BiteDash Order ${orderId}`,
      returnUrl: RETURN_URL,
      resultUrl: RESULT_URL,
    });

    if (!result.ok || !result.pollUrl || !result.browserUrl) {
      logger.error(`initiatePaynowPayment failed for order ${orderId}: ${result.error}`);
      throw new HttpsError("internal", result.error || "Paynow rejected the payment request");
    }

    const paymentDoc = await db.collection(PAYMENTS_COLLECTION).add({
      userId: uid,
      orderId,
      amount,
      currency: "USD",
      status: "PENDING",
      method: method || "PAYNOW",
      mobileMoneyNumber,
      pollUrl: result.pollUrl,
      paynowReference: reference,
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });

    return {
      transactionId: paymentDoc.id,
      browserUrl: result.browserUrl,
      pollUrl: result.pollUrl,
    };
  }
);

/**
 * Actively check a payment's status with Paynow. Used by the app while the
 * customer is completing checkout; the resulturl webhook below is the
 * backstop that still marks the order paid if the app isn't polling
 * anymore (backgrounded, killed, etc.).
 */
export const checkPaynowPaymentStatus = onCall(
  { secrets: [PAYNOW_INTEGRATION_KEY], region: REGION },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Sign in required");
    }
    const transactionId = String(request.data?.transactionId || "");
    if (!transactionId) {
      throw new HttpsError("invalid-argument", "transactionId is required");
    }

    const paymentRef = db.collection(PAYMENTS_COLLECTION).doc(transactionId);
    const paymentSnap = await paymentRef.get();
    if (!paymentSnap.exists) {
      throw new HttpsError("not-found", "Payment not found");
    }
    const payment = paymentSnap.data() as { userId?: string; status?: string; pollUrl?: string };
    if (payment.userId !== request.auth.uid) {
      throw new HttpsError("permission-denied", "This payment does not belong to you");
    }

    if (payment.status === "PAID" || payment.status === "FAILED" || payment.status === "CANCELLED") {
      return { status: payment.status };
    }

    const poll = await pollTransactionStatus(payment.pollUrl || "", PAYNOW_INTEGRATION_KEY.value().trim());
    if (!poll.ok) {
      // Transient/network issue — report PENDING so the app keeps polling
      // instead of treating a hiccup as a hard failure.
      logger.warn(`checkPaynowPaymentStatus poll issue for ${transactionId}: ${poll.error}`);
      return { status: "PENDING" };
    }
    if (poll.status === "PENDING") {
      return { status: "PENDING" };
    }

    const applied = await applyVerifiedStatus(db, transactionId, poll.status, poll.paidAmount, poll.paynowReference);
    return applied;
  }
);

/**
 * Paynow's resulturl callback. Paynow POSTs here server-to-server whenever a
 * transaction's status changes, independent of whether the app is open or
 * polling — this is what keeps a payment from being silently lost if the
 * customer backgrounds/kills the app right after paying.
 */
export const paynowResultWebhook = onRequest(
  { secrets: [PAYNOW_INTEGRATION_KEY], region: REGION },
  async (req, res) => {
    if (req.method !== "POST") {
      res.status(405).send("Method not allowed");
      return;
    }

    const rawBody = (req.rawBody || Buffer.from("")).toString("utf8");
    const verified = validatePaynowHash(rawBody, PAYNOW_INTEGRATION_KEY.value().trim());
    if (!verified) {
      logger.error("paynowResultWebhook: hash validation failed, rejecting message");
      res.status(400).send("Invalid hash");
      return;
    }

    const reference = verified.get("reference") || "";
    const paynowReference = verified.get("paynowreference") || "";
    const amount = parseFloat(verified.get("amount") || "0") || 0;
    const rawStatus = (verified.get("status") || "").toUpperCase();
    const status: PaynowPaymentStatus =
      rawStatus === "PAID" || rawStatus === "DELIVERED"
        ? "PAID"
        : rawStatus === "CANCELLED"
        ? "CANCELLED"
        : rawStatus === "AWAITING_DELIVERY" || rawStatus === "SENT" || rawStatus === "CREATED"
        ? "PENDING"
        : "FAILED";

    if (!reference) {
      logger.error("paynowResultWebhook: message had no reference, ignoring");
      res.status(200).send("OK");
      return;
    }

    const matches = await db
      .collection(PAYMENTS_COLLECTION)
      .where("paynowReference", "==", reference)
      .limit(1)
      .get();

    if (matches.empty) {
      logger.error(`paynowResultWebhook: no payment found for reference ${reference}`);
      res.status(200).send("OK");
      return;
    }

    if (status !== "PENDING") {
      await applyVerifiedStatus(db, matches.docs[0].id, status, amount, paynowReference);
    }

    res.status(200).send("OK");
  }
);

/**
 * Static landing page Paynow redirects the customer's browser back to after
 * hosted checkout. The app should be listening for the payment to complete
 * via polling/the payment doc regardless of whether the customer taps back
 * into the app from here.
 */
export const paynowReturn = onRequest({ region: REGION }, (_req, res) => {
  res.status(200).set("Content-Type", "text/html").send(
    `<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1">
    <title>BiteDash Payment</title></head>
    <body style="font-family: sans-serif; text-align: center; padding: 48px 24px;">
    <h2>Thanks!</h2>
    <p>You can return to the BiteDash app now — we're confirming your payment.</p>
    </body></html>`
  );
});

// ---------------------------------------------------------------------------
// Order placement
// ---------------------------------------------------------------------------

const RESTAURANTS_COLLECTION = "restaurants";
const MENU_ITEMS_COLLECTION = "menu_items";
const USERS_COLLECTION = "users";
const MAX_LINE_ITEMS = 30;
const MAX_QUANTITY_PER_ITEM = 50;
const MAX_DRIVER_TIP = 100;
// Canonical payment-method values the app sends (see mapToFirestorePaymentMethod).
const ALLOWED_PAYMENT_METHODS = new Set([
  "ECO_CASH", "ONE_MONEY", "INNBUCKS", "OMARI", "TELECASH", "ZIPIT", "BANK_CARDS", "CASH_ON_DELIVERY",
]);

const round2 = (n: number): number => Math.round(n * 100) / 100;

/**
 * Create an order, pricing it here instead of trusting the client.
 *
 * The client used to write the order document itself, including its prices and
 * totalCost. initiatePaynowPayment charges whatever totalCost is on the order
 * ("amount always comes from the order document"), so a tampered client could
 * set its own price and pay that. Here the client only says WHAT it wants
 * (restaurant, menu item ids, quantities, tip) and every price, the delivery
 * fee and the total are read from Firestore. Unapproved restaurants and sold-out
 * items are refused here too, not just hidden by the UI.
 */
export const placeOrder = onCall({ region: REGION }, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign in required");
  }
  const uid = request.auth.uid;
  const data = request.data ?? {};

  const restaurantId = String(data.restaurantId || "");
  const paymentMethod = String(data.paymentMethod || "");
  const deliveryAddress = String(data.deliveryAddress || "").trim().slice(0, 300);
  const customerPhone = String(data.customerPhone || "").trim().slice(0, 30);
  const manualMode = data.manualMode !== false;
  const driverTip = round2(Number(data.driverTip ?? 0));

  if (!restaurantId) {
    throw new HttpsError("invalid-argument", "restaurantId is required");
  }
  if (!ALLOWED_PAYMENT_METHODS.has(paymentMethod)) {
    throw new HttpsError("invalid-argument", "Unsupported payment method");
  }
  if (!deliveryAddress) {
    throw new HttpsError("invalid-argument", "A delivery address is required");
  }
  if (!Number.isFinite(driverTip) || driverTip < 0 || driverTip > MAX_DRIVER_TIP) {
    throw new HttpsError("invalid-argument", `Tip must be between 0 and ${MAX_DRIVER_TIP}`);
  }

  // Merge repeated lines for the same item, validating ids and quantities.
  const rawItems: unknown[] = Array.isArray(data.items) ? data.items : [];
  if (rawItems.length === 0 || rawItems.length > MAX_LINE_ITEMS) {
    throw new HttpsError("invalid-argument", `An order needs between 1 and ${MAX_LINE_ITEMS} items`);
  }
  const quantities = new Map<string, number>();
  for (const raw of rawItems) {
    const line = (raw ?? {}) as { menuItemId?: unknown; quantity?: unknown };
    const menuItemId = String(line.menuItemId || "");
    const quantity = Number(line.quantity);
    if (!menuItemId || menuItemId.includes("/") || !Number.isInteger(quantity) || quantity < 1) {
      throw new HttpsError("invalid-argument", "Each item needs a menuItemId and a whole quantity of at least 1");
    }
    const merged = (quantities.get(menuItemId) ?? 0) + quantity;
    if (merged > MAX_QUANTITY_PER_ITEM) {
      throw new HttpsError("invalid-argument", `At most ${MAX_QUANTITY_PER_ITEM} of one item per order`);
    }
    quantities.set(menuItemId, merged);
  }

  const restaurantSnap = await db.collection(RESTAURANTS_COLLECTION).doc(restaurantId).get();
  const restaurant = restaurantSnap.data();
  if (!restaurantSnap.exists || !restaurant || restaurant.isActive === false) {
    throw new HttpsError("not-found", "Restaurant not found");
  }
  if (restaurant.isApproved === false) {
    throw new HttpsError("failed-precondition", "This restaurant isn't taking orders yet");
  }

  const menuRefs = [...quantities.keys()].map((id) => db.collection(MENU_ITEMS_COLLECTION).doc(id));
  const menuSnaps = await db.getAll(...menuRefs);

  let subtotal = 0;
  const items = menuSnaps.map((snap) => {
    const item = snap.data();
    if (!snap.exists || !item || item.restaurantId !== restaurantId) {
      throw new HttpsError("invalid-argument", "An item in your cart isn't on this restaurant's menu");
    }
    if (item.isAvailable === false) {
      throw new HttpsError("failed-precondition", `${item.name || "An item"} is sold out`);
    }
    const price = Number(item.price);
    if (!Number.isFinite(price) || price <= 0) {
      logger.error(`placeOrder: menu item ${snap.id} has an invalid price`, { price: item.price });
      throw new HttpsError("failed-precondition", `${item.name || "An item"} can't be ordered right now`);
    }
    const quantity = quantities.get(snap.id) as number;
    subtotal += price * quantity;
    return {
      menuItemId: snap.id,
      itemId: snap.id,
      name: String(item.name || ""),
      itemName: String(item.name || ""),
      price,
      quantity,
      notes: "",
    };
  });

  const deliveryFeeRaw = Number(restaurant.deliveryFee);
  const deliveryFee = Number.isFinite(deliveryFeeRaw) && deliveryFeeRaw > 0 ? round2(deliveryFeeRaw) : 0;
  subtotal = round2(subtotal);
  const totalCost = round2(subtotal + deliveryFee + driverTip);

  const userSnap = await db.collection(USERS_COLLECTION).doc(uid).get();
  const profile = userSnap.data() ?? {};

  const isCash = paymentMethod === "CASH_ON_DELIVERY";
  const itemsSummary = items.map((i) => `${i.name} x${i.quantity}`).join(", ");
  const status = manualMode ? "PENDING_ACCEPTANCE" : "PREPARING";

  const orderRef = db.collection(ORDERS_COLLECTION).doc();
  await orderRef.set({
    userId: uid,
    restaurantId,
    restaurantName: String(restaurant.name || ""),
    restaurantAddress: String(restaurant.location || ""),
    restaurantPhone: "",
    customerName: String(profile.displayName || ""),
    customerAddress: deliveryAddress,
    customerPhone,
    itemsSummary,
    items,
    subtotal,
    deliveryFee,
    driverTip,
    totalCost,
    status,
    statusHistory: [],
    driverId: null,
    driverName: null,
    deliveryStatus: "UNASSIGNED",
    paymentMethod,
    paymentRef: "",
    paymentStatus: isCash ? "CASH_ON_DELIVERY" : "PENDING",
    isSettled: false,
    restaurantPayoutAmount: 0,
    driverPayoutAmount: 0,
    platformFee: 0,
    createdAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp(),
  });

  logger.info(`placeOrder: created order ${orderRef.id} for ${uid}`, { restaurantId, totalCost });
  return {
    orderId: orderRef.id,
    restaurantName: String(restaurant.name || ""),
    itemsSummary,
    subtotal,
    deliveryFee,
    driverTip,
    totalCost,
    status,
  };
});
