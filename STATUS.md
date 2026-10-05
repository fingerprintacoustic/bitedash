# BiteDash status

Last updated: 2026-10-04. Firebase project: `bitedash-1e078`. Branch: `main` (everything below is pushed).

## Deployed to production (matches `main`)

- **Firestore rules** (`firestore.rules`): restaurant owners/staff cannot change `isApproved`; customers can only
  create *new* orders (status `PENDING_ACCEPTANCE`/`PREPARING`, unassigned, unsettled, payment `PENDING` or
  `CASH_ON_DELIVERY`). Since 2026-10-04 also: `ratings/{orderId}` (see **Customer ratings**), and owners/staff can't
  write a restaurant's `avgRating`/`ratingCount`/`ratingSum`.
- **Cloud Functions** (`functions/`, us-central1): `initiatePaynowPayment`, `checkPaynowPaymentStatus`,
  `paynowResultWebhook`, `paynowReturn`, **`placeOrder`** (prices an order server-side from Firestore), and
  **`onRatingCreated`** (deployed 2026-10-04; the project's first Firestore-triggered, 2nd-gen event function).

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
read `drivers/{uid}` once instead of listening to it. **Fixed in 6.23**, where the tab now uses
`FirestoreService.getDriverFlow()`. **Verified on the 6.23 release build installed from Play closed testing**
(2026-10-03, after a first check on a debug build). A temporary driver was signed up and left on "Registration
Submitted", then approved in Firestore without touching the phone. Within 3 s the screen moved on to the rider guide
and then the Driver Dashboard, with no restart. Switch Role still landed on the role screen ("Go to My Rider
Dashboard") and stayed there, the button returned to the dashboard, and Sign Out worked. Each temporary account was
deleted afterwards (Auth user, `users/` and `drivers/` docs), and none of them exist anymore.
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
sees the order (same "hide until paid" pattern as online orders). From **6.24** this is controlled from Firestore,
not the app code (see **Checkout settings** below): set `paynowLive` to `true` once Paynow is live to return these
five methods to the normal automatic Paynow flow, with no new app version. (6.23 and older have it hard-coded off.)
Receiving numbers are set (`public_settings/payment`, entered by the admin 2026-09-22; checked 2026-10-04):
EcoCash, InnBucks, O'Mari and Telecash 0772673352, OneMoney 0712592526. Verified end to end on a device and over REST, including that the
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
- To do: click "Request to be Set Live" in Paynow. Once it's live: delete `functions/.env` and redeploy (above),
  set `paynowLive: true`, and add any channel that can't work yet (e.g. OneMoney, Telecash, O'Mari) to
  `unavailableMethods`. All of that is in Firestore, so **no new AAB is needed** (6.24 and later).

## Checkout settings: Firestore `public_settings/checkout` (6.24 and later)

Changeable in the Firebase console (Firestore → `public_settings` → `checkout`) without a new app version. The app
picks up changes live, without a restart. Readable by any signed-in user; only admins can write (existing
`public_settings` rule). Current values (set 2026-10-04) match the app's built-in defaults:
- `paynowLive` (boolean, `false`): `false` means EcoCash/OneMoney/InnBucks/Telecash/O'Mari are paid manually (Manual
  Pay); `true` sends them through Paynow.
- `unavailableMethods` (array of strings, `["ZIPIT", "Bank Cards"]`): channels shown at checkout but blocked with "Not
  available yet". Use the exact labels: EcoCash, InnBucks, OneMoney, O'Mari, Telecash, ZIPIT, Bank Cards, USD Cash.
- `zigPerUsd` (number, `0`): ZiG per US dollar for the "≈ ZiG" line under the checkout total. `0` hides the line.
  (It used to show a made-up, hard-coded rate of 22.)

All three were tested on a device (debug build, 2026-10-04): each change showed up on the open checkout screen within
a few seconds, and setting the defaults back restored the normal behaviour.

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
- The rest of the app on a release-signed build. Only phone login and Switch Role were tested on 6.22, and only rider
  approval and Switch Role on 6.23.
- Simulation mode: from 6.24 customers can no longer switch it on (the "Manual Multi-Role Mode" checkbox is gone), so
  every real order follows its real status. 6.23 and older still have the checkbox.
- The 6.24 fixes on a release build (they were tested on a debug build; see "6.24 fixes" below).

## Known open items

- Client order creation is still allowed by the rules (needed by older app builds). Once the version that uses
  `placeOrder` is what everyone has, change the `orders` create rule to `allow create: if false;`.

## Customer ratings (6.24; server side live since 2026-10-04)

Replaces the made-up ratings: every restaurant used to show a number typed into the code (5.0 when an owner created
it, 4.5 when an admin added a brand) and nobody could rate anything.
- A customer rates a **delivered** order 1–5 stars from History ("Rate <restaurant>"), once per order. Afterwards the
  card shows "You rated this order ★★★★☆".
- `ratings/{orderId}` holds `orderId`, `userId`, `stars`, `createdAt`. The rules allow create only by the customer who
  placed that order, only if the order is `COMPLETED`, and only once (no update/delete). The Cloud Function
  `onRatingCreated` then adds it to the restaurant's `ratingCount`, `ratingSum` and `avgRating` in a transaction.
  It looks the restaurant up from the order, so a rating can't target another restaurant, and it marks the rating
  `counted` so a repeated event can't count it twice.
- 6.24 shows `avgRating` ("4.0" on Browse, "4.0 / 5.0" on the restaurant page), or **"New"** before the first rating.
  The old hand-set `rating` field is left alone, so 6.23 and older keep showing what they showed before.
- Existing restaurants all start as "New" in 6.24, since they have no real ratings yet.
- Tested 2026-10-04. **Server:** 13 checks over the Firestore API with temporary accounts. A customer's own
  delivered order could be rated. Rating twice, an undelivered order, someone else's order, 6 stars, extra fields,
  and an owner editing their own `avgRating` were all refused. The count and average updated correctly and the legacy
  field was unchanged. **On the phone (debug build):** a new restaurant showed "New, no ratings yet". After the order
  was delivered, History offered "Rate BD Test Kitchen". 4 stars saved, and the restaurant then showed 4.0 on Browse
  and 4.0 / 5.0 on its page. All test data was deleted afterwards and checked to be gone.
- Not built: a rating count next to the average (it would need a local database change, which in this app wipes
  customers' order history), and rating riders.

## 6.24 fixes: all tested together on a device (debug build, 2026-10-03)

One full run on the test phone with temporary customer, restaurant, rider and admin accounts. The run covered a menu,
four cash orders (one delivered, three rejected), approval, and a role change. Unit tests pass and lint has no errors.
**All test data was deleted afterwards**: 4 Auth users, their `users/` docs, the `drivers/` and `restaurants/` docs,
3 `menu_items` and 4 `orders`. A re-check found none left.

Fixed on Oct 2 (already in 6.22/6.23) but never tested on a device until now. All four pass:
- Admin Users tab: "Current role" updates right after Apply Role Change (Customer → Restaurant → Customer).
- Manage Menu keeps its order after Save (sorted by category, then name, the same as the customer's menu).
  **New in 6.24:** a newly added item also goes straight into its sorted place, so the list no longer jumps on Save.
- "Prepare" fits on one line.
- A rejected/cancelled order no longer vanishes silently. **New in 6.24:** the Oct 2 fix only worked when the
  customer had no other active order; with a second order in progress, Tracking silently switched to it. Now a
  dismissible red banner ("BD Test Kitchen rejected one of your orders") shows above the other order. Tested by
  rejecting an order while the customer watched Tracking.

Other bugs found and fixed for 6.24:
- **Cash orders said "Payment Confirmed! Mobile Money cleared successfully"**, although nothing had been paid. It
  now says "Order Placed! … Pay the rider $X in USD cash when your food arrives".
- **The order confirmation was never seen.** The app jumped straight to Tracking, so the confirmation popped up
  later over the customer's *next* checkout instead. The customer now stays on it, and "Track Delivery" goes to
  Tracking.
- **Cash orders could be placed with "07" as the phone number** (the prefilled prefix), leaving the rider no way to
  call. It now needs at least 9 digits, and checkout prefills the customer's profile phone, as it already did for the
  address.
- **Restaurant "Cancel" on a preparing order did nothing**: it was never connected. It now cancels the order
  (`RestaurantOrderViewModel.cancelOrder`), and the customer gets the cancelled notice.
- **Reject/Cancel acted on a single tap.** They now ask "Reject this order? … This can't be undone" first, with a
  "Keep order" option.
- **"Mark Ready for Pickup" wrapped onto two lines.** It is now "Ready". On the Dashboard tab, "Accept & Start Cook"
  (it only accepts) is now "Accept" and "Mark Cooked & Ready" is now "Ready".
- **History showed raw codes** like `READY_FOR_PICKUP`. It now shows "Ready for Pickup", with a red badge for
  Rejected/Cancelled. "Paid with:" (on unpaid cash orders) is now "Payment:". The admin Orders tab also shows
  readable statuses.
- **"Navigate to Restaurant/Customer" always opened maps in the browser** on Android 11+, even with Google Maps
  installed. It now opens the Maps app (tested), falling back to the browser.
- Prices on rider and restaurant order cards are always formatted as `$2.00`, whatever the phone's language.
- Smaller text fixes: the empty Tracking tab no longer lists every payment method except cash or mentions a "GPS
  simulator". "How to use BiteDash as a administrator" now says "an administrator".

Added 2026-10-04, so a future change doesn't need a new version (tested on a device, debug build):
- **Paynow on/off, unavailable channels and the ZiG rate now come from Firestore** (see **Checkout settings**).
  `PAYNOW_LIVE` used to be a constant, so turning Paynow on needed a new release. The checkout screen also ignored it
  when deciding whether to show the manual-payment steps, so flipping it alone wouldn't even have worked fully.
- **"Manual Multi-Role Mode" checkbox removed** from the customer role screen. Unticking it gave a customer's real
  order a fake, simulated delivery on Tracking. The customer blurb above it was rewritten.
- A channel marked unavailable now says "Not available yet" instead of describing its normal payment flow, and the
  "aren't available yet" message suggests only channels that are actually on.

Also tested in this run and working: sign-up for all four roles, restaurant setup and approval, the cash order from
checkout through accept/prepare/ready, rider claim/pick-up/deliver (`COMPLETED` / `DELIVERED` in Firestore), rider
approval moving the open screen on, and Switch Role from the customer header icon.

## Test data: cleaned up (2026-09-22)

All `bd-test-*` test data has been removed from the production Firebase project:
- All 5 test accounts (`bd-test-customer/-restaurant/-driver/-driver2/-admin@example.com`) deleted from both
  Firestore (`users/`) and Firebase Authentication — verified none of them can sign in anymore. The admin account
  in particular no longer exists at all, not just demoted.
- `BD Test Kitchen` hidden (`isActive: false`, same mechanism as the existing "Delete Restaurant" admin action) —
  verified an unauthenticated read of its document is now refused (403), same as it would be for any other hidden
  restaurant. Its 2 `menu_items` deleted.
- Both test `drivers/` documents, all 16 test `orders/`, and 3 `payments/` records deleted.
- `public_settings/payment` (the manual-payment receiving numbers) was left empty by the cleanup; the admin entered
  the real numbers later the same day (see **Manual mobile-money payments**).

No remaining `bd-test-*` accounts or data. `feature/pesepay` branch (see below) is unaffected — it has no commits.

## App features

- Each role (customer, restaurant, rider, admin) has an (i) button in its top bar that opens a how-to guide; it also
  opens once per account the first time the role is used (`ui/screens/help/RoleGuide.kt`).

## Release

The signed AAB is built by `.github/workflows/build-release-aab.yml` (manual run or a `release-*` tag) from the
repository secrets `KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD`, `PAYNOW_INTEGRATION_ID`,
`PAYNOW_INTEGRATION_KEY`. `app/build.gradle.kts` is at `versionCode = 31` / `versionName = "6.24"`.

**6.24 (versionCode 31) is built, ready to upload to closed testing.** Built 2026-10-04 from `main` at commit
`b07f438` (the PR #29 merge; Actions run 37252175110), signed with the upload key (CN=Fingerprint Acoustic). It has
the 6.24 fixes, the Firestore checkout settings and customer ratings. Copied to
`C:\Users\finge\Downloads\BiteDash-6.24-code31-release.aab` (SHA-256 `5d5d10ea…f36217`).
Why 31: the Play Console refused versionCode 30 as "already used", although no bundle 30 was listed (a code stays
used once a bundle with it has been uploaded, even if it's removed from a draft). **Never reuse 30**; the 6.24
code-30 builds (runs 37177228835, 37202818135, 37207840182) can't be uploaded.

The latest build on Play is **6.23 (versionCode 29)**, made 2026-10-03 from `main` at commit `739fa87` (the PR #21
merge; Actions run 37168645007, artifact `BiteDash-release-aab`). It adds the live rider-approval fix (PR #20) on top
of 6.22. It is copied to `C:\Users\finge\Downloads\BiteDash-6.23-code29-release.aab` and is signed with the same
upload certificate as 6.22 (CN=Fingerprint Acoustic), not the debug key. It was **uploaded straight to closed
testing** on 2026-10-03 at 9:42 PM, skipping internal testing. Uploading is done by hand in the Play Console:
the AAB is too large for browser automation, and there is no Play publishing API set up. The previous build, 6.22
(versionCode 28, commit `cd92a1f`, run 37151661448), is still in Downloads.

### Google Play tracks (as of 2026-10-03)

- **No production release yet.** The app is in **closed testing**. As a new personal developer account, it needs
  at least **12 testers opted in to the closed test for 14 days in a row** before production access can be
  requested. There are fewer than 12 testers so far.
- **Internal testing:** 6.22 (versionCode 28) was tested from here, and phone login was verified on it, installed from
  Play. The Play Console's "Latest releases" overview (checked 2026-10-03) lists internal testing as release
  `0.0.0.4` (versionCode 4, Jun 24, 2026), so 6.22 may no longer be the release shown on that track.
- **Closed testing ("Bitedash tester" track):** **6.23 (versionCode 29)**, "Available to testers on Google Play", full
  rollout, uploaded directly on 2026-10-03 at 9:42 PM. It replaces 6.22, which had been promoted from internal testing
  earlier that day (and which had replaced 6.21). The test phone was offered the update in the Play Store right away
  and installed 6.23 from Play. The rider-approval fix and Switch Role passed on that install (see above). Play
  updates testers automatically, or they can tap Update on the BiteDash page in the Play Store.
- Once production access is granted, promote the newest tested build to production.

## Branches

`feature/pesepay`: created locally, empty (no commits) — set aside for a possible Pesepay integration if Paynow's
card support doesn't work out. Not pushed. Safe to delete if not needed.
