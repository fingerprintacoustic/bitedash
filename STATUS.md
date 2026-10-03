# BiteDash status

Last updated: 2026-10-03. Firebase project: `bitedash-1e078`. Branch: `main` (everything below is pushed).

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

## Phone/SMS (OTP) login: test passed (2026-10-03), needs a release to reach users

Tested on a device (debug build) with the Firebase test number `+263 77 000 3434` (fixed code; no real SMS sent),
signing up as Delivery Driver: code requested, code entered, Auth user created, `users/{uid}` created with
`role: "driver"`, the phone number and display name, and the app opened on the Rider tab's "Set Up Your Rider
Profile". The Rider tab still came up after the app was force-stopped and reopened, so the role was saved.
**Test account removed straight after:** the `users/` document and the Auth user were both deleted (verified that
neither exists anymore); no `drivers/` document was ever created for it.

Phone login was broken for every user before this. Four things were fixed:
- Firebase console, Authentication → Settings → SMS region policy: was "Allow" with **no regions**, so no SMS could
  be sent anywhere (error 17006). Now "Deny" with Nigeria and India only.
- `MainActivity.kt`: the "code sent" state showed a permanent "Please wait…" spinner instead of the code-entry
  screen. It also sent a failed phone sign-in back to the email login screen.
- `FirestoreService.createUser`: wrote the profile to a random document ID, which the rules reject, so phone users
  never got a `users/` document (and fell back to Customer after a restart). Now writes `users/{uid}`.
- `AuthViewModel`: the phone sign-up now waits for that profile write before routing on the role.

The three code fixes are **not in 6.21** (the build on Play), so phone login stays broken for users until a new
release ships. The test number used here, `+263 77 000 3434` (code 246810), is configured under Sign-in method →
Phone. That list also holds `+263 77 123 4567`, `+263 77 222 2222` and `+263 77 333 3333` (code 123456), which are
real numbers, not test numbers. While they're on the list, those people never get a real SMS, and anyone who enters
one of those numbers with code 123456 signs straight into that person's account. The Android app has no SHA certificate
fingerprints registered in Firebase. Phone auth currently works through Play Integrity; adding the Play App Signing
SHA-1 is a recommended backstop.

## Manual mobile-money payments (works right now, independent of Paynow)

Added 2026-09-22: while Paynow isn't live, checkout for EcoCash/OneMoney/InnBucks/Telecash/O'Mari shows the
business's own receiving number and asks the customer for the transfer reference, instead of using Paynow at all.
An admin checks the number and confirms in the new **Manual Pay** tab of the Admin Control Hub before the restaurant
sees the order (same "hide until paid" pattern as online orders). Controlled by a single flag,
`BiteDashViewModel.PAYNOW_LIVE` (currently `false`) — flip it once Paynow is live to return these five methods to
the normal automatic Paynow flow; nothing else needs to change. Set the receiving numbers in Manual Pay before
relying on this (nothing is pre-filled). Verified end to end on a device and over REST, including that the
restaurant cannot see an unconfirmed order.

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

## Test data: cleaned up (2026-09-22)

All `bd-test-*` test data has been removed from the production Firebase project:
- All 5 test accounts (`bd-test-customer/-restaurant/-driver/-driver2/-admin@example.com`) deleted from both
  Firestore (`users/`) and Firebase Authentication — verified none of them can sign in anymore. The admin account
  in particular no longer exists at all, not just demoted.
- `BD Test Kitchen` hidden (`isActive: false`, same mechanism as the existing "Delete Restaurant" admin action) —
  verified an unauthenticated read of its document is now refused (403), same as it would be for any other hidden
  restaurant. Its 2 `menu_items` deleted.
- Both test `drivers/` documents, all 16 test `orders/`, and 3 `payments/` records deleted.
- `public_settings/payment` (the manual-payment receiving numbers) is empty — nothing pre-filled; an admin must
  enter real numbers before manual mobile-money payments are actually usable by customers.

No remaining `bd-test-*` accounts or data. `feature/pesepay` branch (see below) is unaffected — it has no commits.

## App features

- Each role (customer, restaurant, rider, admin) has an (i) button in its top bar that opens a how-to guide; it also
  opens once per account the first time the role is used (`ui/screens/help/RoleGuide.kt`).

## Release

The signed AAB is built by `.github/workflows/build-release-aab.yml` (manual run or a `release-*` tag) from the
repository secrets `KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD`, `PAYNOW_INTEGRATION_ID`,
`PAYNOW_INTEGRATION_KEY`. `app/build.gradle.kts` is at `versionCode = 28` / `versionName = "6.22"`; the version code
must be higher than the highest one already uploaded to Google Play (raise it if Play already has 26 or more).
A release build of 6.20 was made from `main` at commit `e9efa0a` (Actions run 35708691912, artifact
`BiteDash-release-aab`), copied to `C:\Users\finge\Downloads\BiteDash-6.20-code26-release.aab` (this replaces any
earlier copy — always the one to upload). It is signed with the
release certificate (CN=Fingerprint Acoustic), not the debug key. Uploading to Play is done in the Play Console.

## Branches

`feature/pesepay`: created locally, empty (no commits) — set aside for a possible Pesepay integration if Paynow's
card support doesn't work out. Not pushed. Safe to delete if not needed.
