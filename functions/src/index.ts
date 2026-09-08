import { initializeApp } from "firebase-admin/app";
import { getFirestore, FieldValue, Firestore } from "firebase-admin/firestore";
import { onCall, onRequest, HttpsError } from "firebase-functions/v2/https";
import { defineSecret } from "firebase-functions/params";
import { logger } from "firebase-functions/v2";
import { initiateTransaction, pollTransactionStatus, validatePaynowHash, PaynowPaymentStatus } from "./paynow";

initializeApp();
const db = getFirestore();

const REGION = "us-central1";
const PROJECT_ID = process.env.GCLOUD_PROJECT;
const FUNCTIONS_HOST = `https://${REGION}-${PROJECT_ID}.cloudfunctions.net`;
const RESULT_URL = `${FUNCTIONS_HOST}/paynowResultWebhook`;
const RETURN_URL = `${FUNCTIONS_HOST}/paynowReturn`;

const PAYNOW_INTEGRATION_ID = defineSecret("PAYNOW_INTEGRATION_ID");
const PAYNOW_INTEGRATION_KEY = defineSecret("PAYNOW_INTEGRATION_KEY");

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

    const result = await initiateTransaction({
      integrationId: PAYNOW_INTEGRATION_ID.value(),
      integrationKey: PAYNOW_INTEGRATION_KEY.value(),
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

    const poll = await pollTransactionStatus(payment.pollUrl || "", PAYNOW_INTEGRATION_KEY.value());
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
    const verified = validatePaynowHash(rawBody, PAYNOW_INTEGRATION_KEY.value());
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
