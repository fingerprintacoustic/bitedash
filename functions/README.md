# BiteDash Cloud Functions — Paynow integration

This is the server-side half of BiteDash's Paynow integration. The Android
app never talks to Paynow directly and never holds the Paynow Integration
Key — it only calls these Cloud Functions, which hold the key, verify every
response Paynow sends back (hash-checked, SHA-512), and are the only thing
with permission to write `PAID` onto a payment/order in Firestore (see
`firestore.rules`).

## Functions

- **`initiatePaynowPayment`** (callable) — looks up the order in Firestore
  (the amount is read from there, never trusted from the client), starts a
  Paynow hosted-checkout transaction, and returns a `browserUrl` for the app
  to open plus a `transactionId` to poll.
- **`checkPaynowPaymentStatus`** (callable) — polls Paynow for a
  transaction's status, verifies the response, and applies it to Firestore
  if changed. The app calls this in a loop while the customer is paying.
- **`paynowResultWebhook`** (HTTPS) — Paynow's server-to-server callback
  (`resulturl`). This is the backstop that still marks an order paid even if
  the customer backgrounds/kills the app right after paying, instead of
  relying only on the app's own polling.
- **`paynowReturn`** (HTTPS) — a tiny static page Paynow redirects the
  customer's browser to after checkout (`returnurl`). Just tells them to
  switch back to the app.

## One-time setup

1. **Firebase CLI + Blaze plan.** Functions that call an external API
   (Paynow) need outbound networking, which requires the Blaze
   (pay-as-you-go) plan on the `bitedash-1e078` project.

   ```
   npm install -g firebase-tools
   firebase login
   firebase use bitedash-1e078
   ```

2. **Set the Paynow secrets** (from your Paynow merchant dashboard →
   Integrations). Use a **test-mode** integration while developing —
   Paynow's test mode uses the exact same API, just without moving real
   money.

   ```
   firebase functions:secrets:set PAYNOW_INTEGRATION_ID
   firebase functions:secrets:set PAYNOW_INTEGRATION_KEY
   ```

3. **Deploy.**

   ```
   firebase deploy --only functions,firestore:rules
   ```

4. Note the deployed URL of `paynowResultWebhook` (printed after deploy, or
   `firebase functions:list`) — it should match
   `https://us-central1-bitedash-1e078.cloudfunctions.net/paynowResultWebhook`,
   which is also what `initiatePaynowPayment` sends Paynow as `resulturl`.
   No extra Paynow-dashboard configuration is needed since `resulturl` is
   sent per-transaction.

## Before going live

- Switch the Firebase secrets to your **live** (non-test) Paynow Integration
  ID/Key once you're ready to accept real money — this alone flips
  everything from test to live, there's no separate sandbox hostname.
- This integration uses Paynow's **hosted checkout** ("Initiate a
  Transaction" → `browserUrl`) rather than the direct EcoCash/OneMoney USSD
  push ("remote transaction") endpoint. That was a deliberate choice: this
  environment's network access couldn't reach
  `developers.paynow.co.zw` to verify the remote endpoint's exact hash field
  order, while the hosted-checkout flow's wire format was independently
  confirmed from multiple sources. It still supports EcoCash, OneMoney,
  InnBucks and card — the customer just picks on Paynow's page instead of
  the app pushing a USSD prompt directly. If you want the native push
  experience later, get the `remotetransaction` field order confirmed
  against the live docs first — an incorrect hash there fails silently
  in exactly the way the previous client-side integration did.
- Load-test / smoke-test a full payment in Paynow test mode before
  switching to live credentials: place an order, pay via the test EcoCash
  flow, confirm the order flips to `paymentStatus: PAID` in Firestore and
  the restaurant/driver apps see it.

## Local development

```
npm install
npm run build
firebase emulators:start --only functions,firestore
```

The emulator still calls Paynow's real (test-mode) API over the network —
Paynow has no local/offline emulator.
