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

## Not verified

- A real Paynow payment (EcoCash / OneMoney / InnBucks / card) and its webhook.
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

## Test data currently in the production Firebase project (delete before/at release)

- Accounts: `bd-test-customer@example.com`, `bd-test-restaurant@example.com`, `bd-test-driver@example.com`,
  `bd-test-driver2@example.com`, `bd-test-admin@example.com` (**has the admin role, known password**).
- Restaurant "BD Test Kitchen" (approved, visible to real customers) and its two menu items.
- Driver documents for the two test drivers; orders placed by the test customer.

## Release

The signed AAB is built by `.github/workflows/build-release-aab.yml` (manual run or a `release-*` tag) from the
repository secrets `KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD`, `PAYNOW_INTEGRATION_ID`,
`PAYNOW_INTEGRATION_KEY`. `app/build.gradle.kts` is at `versionCode = 24` / `versionName = "6.18"`; the version code
must be higher than the highest one already uploaded to Google Play.
