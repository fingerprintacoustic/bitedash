# BiteDash status

Last updated: 2026-09-21. Firebase project: `bitedash-1e078`. Branch: `main` (everything below is pushed).

## Deployed to production (matches `main`)

- **Firestore rules** (`firestore.rules`): restaurant owners/staff cannot change `isApproved`; customers can only
  create *new* orders (status `PENDING_ACCEPTANCE`/`PREPARING`, unassigned, unsettled, payment `PENDING` or
  `CASH_ON_DELIVERY`).
- **Cloud Functions** (`functions/`, us-central1): `initiatePaynowPayment`, `checkPaynowPaymentStatus`,
  `paynowResultWebhook`, `paynowReturn`, and **`placeOrder`** (prices an order server-side from Firestore).

## App behaviour verified on a device (debug build)

Sign-up and role gating for customer / restaurant / driver / admin, Switch Role, sold-out toggle, admin role change,
driver and restaurant approval, and the full Cash on Delivery lifecycle (place, accept, prepare, ready, driver
accepts/picks up/delivers, customer sees live status). Checkout now goes through `placeOrder`.
Live rules were checked over REST as customer, driver, owner and signed-out user.

## Online payments (Paynow): EcoCash express checkout verified in TEST mode

- 2026-09-21: the Paynow integration ("BiteDash Food Delivery") is in **test mode** ("The External Site is in
  testing"), so real customers cannot pay online until Paynow sets it live. Cash on Delivery is unaffected.
- EcoCash/OneMoney/InnBucks now use Paynow express checkout (approved on the customer's phone, inside the app).
  Verified in test mode with Paynow's test number 0771111111: payment record `PAID` (mode express), order
  `paymentStatus PAID` with a Paynow reference set.
- Test mode only accepts the merchant login email as payer, supplied by the git-ignored `functions/.env`
  (`PAYNOW_AUTH_EMAIL_OVERRIDE`). **Delete that file and redeploy `initiatePaynowPayment` when Paynow sets the
  integration live**, otherwise every customer payment would use the merchant email.
- Enabled on the Paynow account (USD only): EcoCash, Zimswitch, PayGo, InnBucks, Internet/Mobile Banking, POS2U.
  Visa/Mastercard are inactive (need business verification). OneMoney and Telecash exist only as ZWG (unticked).
  Do not tick ZWG methods: the app sends USD amounts.
- To do: click "Request to be Set Live" in Paynow; then hide channels that can't work (OneMoney, Telecash, O'Mari,
  ZIPIT, Bank Cards) until enabled; rebuild the AAB.

### Earlier notes (hosted-page flow, still used for cards)

Every online channel (EcoCash, OneMoney, InnBucks, O'Mari, Telecash, ZIPIT, Bank Cards) goes through Paynow's hosted
checkout: the server (`initiatePaynowPayment`) starts the transaction and the app opens Paynow's page. The channel
and number typed in the app are not sent to Paynow; the customer picks how to pay on Paynow's page.

- Fixed in the app: the four channels other than EcoCash/OneMoney/InnBucks used to fail with "Cash on Delivery does
  not go through Paynow" before reaching Paynow. They now reach Paynow.
- Fixed (2026-09-21): Paynow was rejecting every request with "Invalid Hash" because the Firebase secret
  `PAYNOW_INTEGRATION_KEY` was the real 36-character key pasted twice (72 characters). It was re-saved as a single
  copy (secret version 2, never printed) and `initiatePaynowPayment`, `checkPaynowPaymentStatus` and
  `paynowResultWebhook` were redeployed. Verified on a device: choosing Bank Cards at checkout now opens Paynow's
  hosted payment page (paynow.co.zw). If the key is ever changed, redeploy those three functions afterwards; they pin
  the secret version at deploy time.
- Paynow's page shows Visa, Mastercard, ZimSwitch, EcoCash, Telecash and OneMoney and asks the payer for an email
  (guest payment). InnBucks, ZIPIT and O'Mari are still listed in the app but were not seen on that page; check what
  the merchant account has enabled before offering them.
- **Still to do before launch:** complete one small real payment and confirm the order flips to paid (the
  `paynowResultWebhook` / `checkPaynowPaymentStatus` path). Until then online payments are unproven end to end.
- Online orders are saved before payment. Restaurants now only see Cash on Delivery orders and orders Paynow has
  confirmed as paid. Abandoned/failed payments are not cancelled: they stay in Firestore, hidden from restaurants.

## Not verified

- A completed Paynow payment (EcoCash / OneMoney / InnBucks / card) and its webhook.
- Phone/SMS (OTP) login.
- A release-signed build (only debug builds were tested).
- Simulation mode (non-manual checkout) does not sync real order status.

## Known open items

- Client order creation is still allowed by the rules (needed by older app builds). Once the version that uses
  `placeOrder` is what everyone has, change the `orders` create rule to `allow create: if false;`.
- Admin "Users" tab: the "Current role" line doesn't refresh after a role change.
- New restaurants get a hard-coded 5.0 rating (`BiteDashMainApp.kt`, restaurant setup).
- Menu items come back in a different order after saving in Manage Menu.
- "Start Preparing" button label wraps onto two lines.
- Rejected/cancelled orders silently drop out of the customer's Tracking tab (History shows the raw status).

## Test data still in the production Firebase project (must be deleted BEFORE publishing)

Cleanup was started and one test order deleted; the rest is still there. Remaining Firestore documents:

- `users/` for the five test accounts: `bd-test-customer@example.com`, `bd-test-restaurant@example.com`,
  `bd-test-driver@example.com`, `bd-test-driver2@example.com`, `bd-test-admin@example.com`
  (**the admin account has the admin role and a known password; deleting its `users/` document removes the role**).
- `drivers/` for the two test drivers, restaurant `BD Test Kitchen` (approved, visible to real customers) and its
  two `menu_items`, and five orders placed by the test customer.
- The five accounts also still exist in Firebase Authentication (they can only be deleted in the console).

## App features

- Each role (customer, restaurant, rider, admin) has an (i) button in its top bar that opens a how-to guide; it also
  opens once per account the first time the role is used (`ui/screens/help/RoleGuide.kt`).

## Release

The signed AAB is built by `.github/workflows/build-release-aab.yml` (manual run or a `release-*` tag) from the
repository secrets `KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD`, `PAYNOW_INTEGRATION_ID`,
`PAYNOW_INTEGRATION_KEY`. `app/build.gradle.kts` is at `versionCode = 26` / `versionName = "6.20"`; the version code
must be higher than the highest one already uploaded to Google Play (raise it if Play already has 26 or more).
A release build of 6.20 was made from `main` (Actions run 35561220151, artifact `BiteDash-release-aab`); it is signed
with the release certificate (CN=Fingerprint Acoustic), not the debug key. Uploading to Play is done in the Play Console.
