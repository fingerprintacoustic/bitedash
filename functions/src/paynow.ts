import * as crypto from "crypto";
import { logger } from "firebase-functions/v2";

/**
 * Paynow Zimbabwe "Initiate a Transaction" (hosted checkout) endpoint.
 *
 * Confirmed against https://developers.paynow.co.zw/docs/paynow/initiate_transaction/
 * and https://developers.paynow.co.zw/docs/paynow/generating_hash/ (fetched via
 * search, direct fetch was blocked by network policy — re-verify against the
 * live docs before going to production). Paynow does not have separate
 * sandbox/production hostnames; a merchant's Integration ID/Key determine
 * whether transactions are live or test.
 */
export const PAYNOW_INITIATE_URL = "https://www.paynow.co.zw/interface/initiatetransaction";

/**
 * Generate the Paynow request hash.
 *
 * Per Paynow's docs: join the given values (in the exact order they'll be
 * sent as form fields) into one string with NO url-encoding, append the
 * integration key, SHA-512 the result, and hex-encode in UPPERCASE.
 */
export function generatePaynowHash(values: string[], integrationKey: string): string {
  const data = values.join("") + integrationKey;
  return crypto.createHash("sha512").update(data, "utf8").digest("hex").toUpperCase();
}

/** Parsed Paynow response as an ordered list of [key, value] pairs plus a map. */
export interface ParsedPaynowMessage {
  fields: Map<string, string>;
  /** Values in wire order, URL-decoded, excluding "hash" — for hash validation. */
  orderedValuesExcludingHash: string[];
}

/**
 * Parse a Paynow response/webhook body: "key=value&key=value...", URL-encoded.
 */
export function parsePaynowMessage(body: string): ParsedPaynowMessage {
  const fields = new Map<string, string>();
  const orderedValuesExcludingHash: string[] = [];

  for (const pair of body.split("&")) {
    if (!pair) continue;
    const eqIndex = pair.indexOf("=");
    if (eqIndex === -1) continue;
    const key = decodeURIComponent(pair.slice(0, eqIndex).trim());
    const rawValue = pair.slice(eqIndex + 1).trim().replace(/\+/g, " ");
    const value = decodeURIComponent(rawValue);
    fields.set(key.toLowerCase(), value);
    if (key.toLowerCase() !== "hash") {
      orderedValuesExcludingHash.push(value);
    }
  }

  return { fields, orderedValuesExcludingHash };
}

/**
 * Validate the hash on an inbound Paynow message (initiate response, poll
 * response, or resulturl webhook body) per
 * https://developers.paynow.co.zw/docs/paynow/validating_hash/.
 *
 * Returns the parsed fields if the hash is valid, or null if it's missing or
 * doesn't match — callers MUST treat null as "reject this message".
 */
export function validatePaynowHash(
  body: string,
  integrationKey: string
): Map<string, string> | null {
  const { fields, orderedValuesExcludingHash } = parsePaynowMessage(body);
  const receivedHash = fields.get("hash");
  if (!receivedHash) return null;

  const expectedHash = generatePaynowHash(orderedValuesExcludingHash, integrationKey);
  if (receivedHash.toUpperCase() !== expectedHash) return null;

  return fields;
}

export interface InitiateTransactionParams {
  integrationId: string;
  integrationKey: string;
  reference: string;
  amount: number;
  additionalInfo: string;
  returnUrl: string;
  resultUrl: string;
}

export interface InitiateTransactionResult {
  ok: boolean;
  browserUrl?: string;
  pollUrl?: string;
  error?: string;
}

/**
 * Call Paynow's "Initiate a Transaction" endpoint. This is the hosted
 * checkout flow: the customer is redirected to browserUrl (a Paynow page)
 * to complete payment via EcoCash, OneMoney, InnBucks, or card, then bounces
 * back to returnUrl. BiteDash uses this for cards (and for older app builds);
 * mobile money is paid on the customer's phone via initiateExpressTransaction
 * below. (Paynow hashes message values in the order they appear, so the express
 * endpoint's field order is simply the order they're sent in.)
 */
export async function initiateTransaction(
  params: InitiateTransactionParams
): Promise<InitiateTransactionResult> {
  const amountStr = params.amount.toFixed(2);
  const status = "Message";

  // Order matters: this exact sequence is both the POST body and the hash input.
  const orderedFields: [string, string][] = [
    ["id", params.integrationId],
    ["reference", params.reference],
    ["amount", amountStr],
    ["additionalinfo", params.additionalInfo],
    ["returnurl", params.returnUrl],
    ["resulturl", params.resultUrl],
    ["status", status],
  ];

  const hash = generatePaynowHash(
    orderedFields.map(([, v]) => v),
    params.integrationKey
  );

  const body = new URLSearchParams();
  for (const [k, v] of orderedFields) body.append(k, v);
  body.append("hash", hash);

  const response = await fetch(PAYNOW_INITIATE_URL, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: body.toString(),
  });

  const responseText = await response.text();
  if (!response.ok) {
    return { ok: false, error: `Paynow HTTP ${response.status}` };
  }

  // Paynow's own error replies (unknown integration id, hash mismatch, invalid
  // amount, ...) carry no hash. Look for one before insisting on a hash: checking
  // the hash first reported every rejection as "failed hash verification" and threw
  // away the real reason. The error text is safe to surface; it holds no secrets.
  const parsed = parsePaynowMessage(responseText);
  if (parsed.fields.get("status")?.toLowerCase() === "error") {
    const paynowError = parsed.fields.get("error") || "the request was rejected";

    // For a rejected hash Paynow says how the correct one starts ("Hash should start
    // with: BB0564"). Use that to tell a whitespace/paste problem in the stored key
    // from a wrong key, logging only yes/no answers and lengths, never the key itself.
    const expectedPrefix = /should start with:\s*([0-9A-Fa-f]+)/.exec(paynowError)?.[1]?.toUpperCase();
    if (expectedPrefix) {
      const values = orderedFields.map(([, v]) => v);
      logger.error("Paynow rejected the request hash", {
        expectedPrefix,
        keyLength: params.integrationKey.length,
        keyHasEdgeWhitespace: params.integrationKey !== params.integrationKey.trim(),
        idHasEdgeWhitespace: params.integrationId !== params.integrationId.trim(),
        hashMatchesAsStored: generatePaynowHash(values, params.integrationKey).startsWith(expectedPrefix),
        hashMatchesTrimmedKey: generatePaynowHash(values, params.integrationKey.trim()).startsWith(expectedPrefix),
        // Paynow keys are normally 36-character GUIDs, so a much longer stored key
        // (e.g. two pasted copies) is the usual reason for a rejected hash.
        keyLooksMalformed: params.integrationKey.length !== 36,
      });
    }

    return { ok: false, error: `Paynow error: ${paynowError}` };
  }

  const verified = validatePaynowHash(responseText, params.integrationKey);
  if (!verified) {
    return { ok: false, error: "Paynow response failed hash verification" };
  }

  if (verified.get("status")?.toLowerCase() !== "ok") {
    return { ok: false, error: verified.get("error") || "Paynow rejected the request" };
  }

  return {
    ok: true,
    browserUrl: verified.get("browserurl"),
    pollUrl: verified.get("pollurl"),
  };
}

export type PaynowPaymentStatus = "PENDING" | "PAID" | "FAILED" | "CANCELLED";

export interface PollResult {
  ok: boolean;
  status: PaynowPaymentStatus;
  paidAmount: number;
  paynowReference: string;
  error?: string;
}

/** Map Paynow's status string to our internal status. */
function mapStatus(paynowStatus: string): PaynowPaymentStatus {
  switch (paynowStatus.toUpperCase().trim()) {
    case "PAID":
    case "DELIVERED":
      return "PAID";
    case "CANCELLED":
      return "CANCELLED";
    case "AWAITING_DELIVERY":
    case "SENT":
    case "CREATED":
      return "PENDING";
    default:
      return "FAILED";
  }
}

/**
 * Poll a Paynow transaction's status. `pollUrl` comes from a prior,
 * hash-verified initiate response, and the response is itself hash-verified
 * here — an unverified response is never trusted.
 */
export async function pollTransactionStatus(
  pollUrl: string,
  integrationKey: string
): Promise<PollResult> {
  const response = await fetch(pollUrl, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: "",
  });

  const responseText = await response.text();
  if (!response.ok || !responseText.trim()) {
    return { ok: false, status: "FAILED", paidAmount: 0, paynowReference: "", error: "Empty/failed poll response" };
  }

  const verified = validatePaynowHash(responseText, integrationKey);
  if (!verified) {
    return {
      ok: false,
      status: "FAILED",
      paidAmount: 0,
      paynowReference: "",
      error: "Paynow poll response failed hash verification",
    };
  }

  return {
    ok: true,
    status: mapStatus(verified.get("status") || ""),
    paidAmount: parseFloat(verified.get("amount") || "0") || 0,
    paynowReference: verified.get("paynowreference") || "",
  };
}

// ---------------------------------------------------------------------------
// Express checkout (mobile money approved on the customer's phone)
// ---------------------------------------------------------------------------

export const PAYNOW_EXPRESS_URL = "https://www.paynow.co.zw/interface/remotetransaction";

/** The mobile-money methods BiteDash pays through Paynow's express checkout. */
export type ExpressMethod = "ecocash" | "onemoney" | "innbucks";

/**
 * Normalise a Zimbabwean mobile number to the local 10-digit form Paynow expects
 * ("0771234567"). Accepts spaces/dashes and the +263 / 263 international forms.
 * Returns null if it isn't a plausible Zimbabwean mobile number.
 */
export function normaliseZimbabweanMobile(input: string): string | null {
  let digits = String(input || "").replace(/[^0-9+]/g, "");
  if (digits.startsWith("+263")) digits = "0" + digits.slice(4);
  else if (digits.startsWith("263")) digits = "0" + digits.slice(3);
  return /^07[1-9][0-9]{7}$/.test(digits) ? digits : null;
}

export interface ExpressTransactionParams extends InitiateTransactionParams {
  /** Required by Paynow for express checkout: the customer's email. */
  authEmail: string;
  /** Subscriber number to debit, in the local 10-digit form. */
  phone: string;
  method: ExpressMethod;
}

export interface ExpressTransactionResult {
  ok: boolean;
  pollUrl?: string;
  paynowReference?: string;
  /** Text to show the customer, e.g. how to approve the prompt on their phone. */
  instructions?: string;
  /** InnBucks only: the code the customer approves in the InnBucks app. */
  authorizationCode?: string;
  authorizationExpires?: string;
  error?: string;
}

/**
 * Start a Paynow express checkout: instead of sending the customer to Paynow's page,
 * Paynow prompts the wallet holder on their own phone (EcoCash / OneMoney) or issues a
 * code to approve in the InnBucks app. The customer never leaves BiteDash.
 *
 * Paynow hashes the message values in the order they appear, so the fields are sent in
 * the same order they're hashed here.
 */
export async function initiateExpressTransaction(
  params: ExpressTransactionParams
): Promise<ExpressTransactionResult> {
  const orderedFields: [string, string][] = [
    ["id", params.integrationId],
    ["reference", params.reference],
    ["amount", params.amount.toFixed(2)],
    ["additionalinfo", params.additionalInfo],
    ["returnurl", params.returnUrl],
    ["resulturl", params.resultUrl],
    ["authemail", params.authEmail],
    ["phone", params.phone],
    ["method", params.method],
    ["status", "Message"],
  ];

  const hash = generatePaynowHash(
    orderedFields.map(([, v]) => v),
    params.integrationKey
  );

  const body = new URLSearchParams();
  for (const [k, v] of orderedFields) body.append(k, v);
  body.append("hash", hash);

  const response = await fetch(PAYNOW_EXPRESS_URL, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: body.toString(),
  });

  const responseText = await response.text();
  if (!response.ok) {
    return { ok: false, error: `Paynow HTTP ${response.status}` };
  }

  // Paynow's own error replies (bad number, insufficient balance, method not enabled on
  // this integration, ...) carry no hash, so read them before insisting on one.
  const parsed = parsePaynowMessage(responseText);
  if (parsed.fields.get("status")?.toLowerCase() === "error") {
    return { ok: false, error: `Paynow error: ${parsed.fields.get("error") || "the request was rejected"}` };
  }

  const verified = validatePaynowHash(responseText, params.integrationKey);
  if (!verified) {
    return { ok: false, error: "Paynow response failed hash verification" };
  }
  if (verified.get("status")?.toLowerCase() !== "ok") {
    return { ok: false, error: verified.get("error") || "Paynow rejected the request" };
  }
  const pollUrl = verified.get("pollurl");
  if (!pollUrl) {
    return { ok: false, error: "Paynow did not return a status URL" };
  }

  return {
    ok: true,
    pollUrl,
    paynowReference: verified.get("paynowreference"),
    instructions: verified.get("instructions"),
    authorizationCode: verified.get("authorizationcode"),
    authorizationExpires: verified.get("authorizationexpires"),
  };
}
