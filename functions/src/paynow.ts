import * as crypto from "crypto";

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
 * back to returnUrl. We use this instead of the "remote transaction"
 * (direct USSD push) endpoint because its wire format is fully documented
 * and verifiable; the remote endpoint's exact hash field order could not be
 * confirmed in this environment (see functions/README.md).
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
