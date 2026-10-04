# BiteDash status

Last updated: 2026-10-03. Firebase project: `bitedash-1e078`. Branch: `main` (everything below is pushed).

## Deployed to production (matches `main`)

- **Firestore rules** (`firestore.rules`): restaurant owners/staff cannot change `isApproved`; customers can only
  create *new* orders (status `PENDING_ACCEPTANCE`/`PREPARING`, unassigned, unsettled, payment `PENDING` or
  `CASH_ON_DELIVERY`).
- **Cloud Functions** (`functions/`, us-central1): `initiatePaynowPayment`, `checkPaynowPaymentStatus`,
  `paynowResultWebhook`, `paynowReturn`, and **`placeOrder`** (prices an order server-side from Firestore).

## App behaviour verified on a device (debug build)

Sign-up and role gating for customer / restaurant / driver / admin, sold-out toggle, admin role change,
driver and restaurant approval, and the full Cash on Delivery lifecycle (place, accept, prepare, ready, driver
accepts/picks up/delivers, customer sees live status). Checkout now goes through `placeOrder`.
Live rules were checked over REST as customer, driver, owner and signed-out user.

## Switch Role on approved Driver/Restaurant accounts: verified on the 6.22 release build (2026-10-03)

**Pass for both.** Tested on a Samsung SM-S176V running 6.22 (versionCode 28) installed from Play. This build
contains all the Switch Role fixes (`UserProfile.SwitchingRole`), and no app code has changed on `main` since.
Two temporary accounts were used: `bd-test-driver@example.com` (`role: driver`, `drivers/{uid}.isApproved: true`) and
`bd-test-restaurant@example.com` (`role: restaurant`, its restaurant `isApproved: true`). Each was signed up in the
app and approved in Firestore, and the app was force-stopped and reopened before testing.
- **Driver:** the app opened on the Driver Dashboard. Tapping Switch Role (top right) went to the "Welcome to
  BiteDash" role screen, with the Rider tab selected, "You're registered as a rider (BD Test Driver)" and a
  **Go to My Rider Dashboard** button. **Sign Out** shows at the top. Screenshots at about 0.3 s, 2 s and 6 s were the
  same, so it did not flash a spinner or bounce back. The Customer and Restaurant tabs both opened. Go to My Rider
  Dashboard returned to the dashboard. A second Switch Role landed on the role screen again, and Sign Out from
  there went to the login screen.
- **Restaurant:** the same result. The role screen opened with the Restaurant tab selected, "You're registered
  as the owner of "BD Test Kitchen 2"" and **Go to My Restaurant Dashboard**. It stayed there after 6 s. The Rider
  tab opened, the button went back to the dashboard, and Sign Out worked.

**Test accounts removed afterwards:** both Auth users, both `users/` docs, the `drivers/` doc and the `restaurants/`
doc were deleted, and none of them exist anymore. No menu items or orders were created.

Side note: on 6.22, a rider waiting on the "Registration Submitted / awaiting admin approval" screen is not moved on
when an admin approves them. The dashboard only appears after the app is reopened. The cause was that the Rider tab
read `drivers/{uid}` once instead of listening to it. **Fixed in 6.23 (built, not yet on Play):** the tab now
uses `FirestoreService.getDriverFlow()`. Verified on a debug build (2026-10-03): a temporary driver was approved in
Firestore while that screen was open, and within 3 s it moved on to the rider guide and then the Driver Dashboard,
with no restart. Switch Role still stayed on the role screen afterwards. The temporary account was deleted (Auth
user, `users/` and `drivers/` docs).
Restaurants were not affected: their tab already uses the live `restaurantsState` listener.

## Phone/SMS (OTP) login: verified on the 6.22 release build (2026-10-03)

Tested twice with the Firebase test number `+263 77 000 3434` (fixed code; no real SMS sent), signing up as
Delivery Driver. First run: debug build. Second run: the release build **6.22 (versionCode 28) installed from Play
internal testing**. Both runs: code requested, code-entry screen shown, code entered, Auth user created,
`users/{uid}` created with `role: "driver"`, the phone number and display name, and the app opened on the Rider
tab's "Set Up Your Rider Profile". The Rider tab still came up after the app was force-stopped and reopened, so the
role was saved.
**Test accounts removed straight after each run:** the `users/` document and the Auth user were both deleted
(verified that neither exists anymore); no `drivers/` document was created for either.

Phone login was broken for every user before this. Four things were fixed:
- Firebase console, Authentication → Settings → SMS region policy: was "Allow" with **no regions**, so no SMS could
  be sent anywhere (error 17006). Now "Deny" with Nigeria and India only.
- `MainActivity.kt`: the "code sent" state showed a permanent "Please wait…" spinner instead of the code-entry
  screen. It also sent a failed phone sign-in back to the email login screen.
- `FirestoreService.createUser`: wrote the profile to a random document ID, which the rules reject, so phone users
  never got a `users/` document (and fell back to Customer after a restart). Now writes `users/{uid}`.
- `AuthViewModel`: the phone sign-up now waits for that profile write before routing on the role.

The code fixes are in 6.22 and above, which is now on the closed testing track (see **Release**). 6.21 and older
builds still have the broken flow, so testers who haven't updated yet can't sign in by phone. **No phone test numbers are configured anymore** (Sign-in method → Phone): the test number
above was removed after the release test passed. `+263 77 123 4567`, `+263 77 222 2222` and `+263 77 333 3333`
(all with code 123456, which anyone could use to sign in) were removed earlier the same day.

The Firebase Android app (Project settings → Your apps) has the **Play App Signing** certificate fingerprints
registered (added 2026-10-03, read from the 6.22 APK installed from Play): SHA-1
`d2:77:2b:d9:34:61:ed:c4:1a:ba:a0:98:53:4c:af:ea:d7:8f:34:c3` and SHA-256
`bc:af:40:17:67:cf:d7:b1:f4:6a:0d:5f:34:17:4e:70:8c:8f:c3:f0:b3:b9:fe:f4:43:ac:84:92:98:e4:7f:9b`. These cover
every install from Play. Sideloaded builds (debug, or the AAB signed with the upload key) have different
certificates that are not registered. Phone login on the debug build worked without them, through Play Integrity.

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
- The rest of the app on a release-signed build (on 6.22, only phone login and Switch Role have been tested).
- Simulation mode (non-manual checkout) does not sync real order status.
- Switch Role from the **customer header** icon on the release build. The Driver and Restaurant Dashboard
  entry points passed on 6.22; see the section above.

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
`PAYNOW_INTEGRATION_KEY`. `app/build.gradle.kts` is at `versionCode = 29` / `versionName = "6.23"`. The version code
must be higher than the highest one already uploaded to Google Play (raise it if Play already has 29 or more).
The latest release build is **6.23 (versionCode 29)**, made 2026-10-03 from `main` at commit `739fa87` (the PR #21
merge; Actions run 37168645007, artifact `BiteDash-release-aab`). It adds the live rider-approval fix (PR #20) on top
of 6.22. It is copied to `C:\Users\finge\Downloads\BiteDash-6.23-code29-release.aab`, which is the one to upload, and
is signed with the same upload certificate as 6.22 (CN=Fingerprint Acoustic), not the debug key. It was uploaded
**straight to closed testing** on 2026-10-03 (see the tracks below). Uploading is done by hand in the Play Console:
the AAB is too large for browser automation, and there is no Play publishing API set up. The previous build, 6.22
(versionCode 28, commit `cd92a1f`, run 37151661448), is still in Downloads.

### Google Play tracks (as of 2026-10-03)

- **No production release yet.** The app is in **closed testing**. As a new personal developer account, it needs
  at least **12 testers opted in to the closed test for 14 days in a row** before production access can be
  requested. There are fewer than 12 testers so far.
- **Internal testing:** still 6.22 (versionCode 28). 6.23 was not put on internal testing. Phone login was verified
  on 6.22, installed from Play.
- **Closed testing:** 6.23 (versionCode 29), uploaded directly to closed testing on 2026-10-03, replacing 6.22
  (which had been promoted from internal testing earlier the same day). It may still be in Play review before
  testers get it. Play updates testers automatically, or they can tap Update on the BiteDash page in the Play Store.
  6.23 has not been installed from Play and checked on a device yet.
- Once production access is granted, promote the newest tested build to production.

## Branches

`feature/pesepay`: created locally, empty (no commits) — set aside for a possible Pesepay integration if Paynow's
card support doesn't work out. Not pushed. Safe to delete if not needed.
