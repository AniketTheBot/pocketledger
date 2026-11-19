# HANDOFF — PocketLedger implementation brief

**Audience: the AI agent that will implement this app.** Read this top to
bottom, then `DESIGNS.md` (chosen design) and `design-tokens.json`, and keep
`sms-parsing-playbook.md` open for the SMS engine (M2).

**Stack decision: Kotlin + Jetpack Compose (native Android). No Flutter.**

---

## 🔒 Locked build decisions (Nov 2026)

- **Chosen design: Layout E · Sticker Ledger + Theme 5 "Sticker Ledger"** — see
  `DESIGNS.md` ★ section. Implement exactly that structure.
- **Fonts:** `Archivo Black` bundled at `res/font/archivoblack_regular.ttf`
  (display); body uses the system font. No runtime font downloads.
- **ML (deviation from §7 wording):** the on-device classifier is a **bundled
  softmax model over hashed character n-gram features**, compiled into
  `ml/BundledModel.kt` and trained reproducibly by
  `tools/train_classifier.mjs` (synthetic bank-SMS corpus). Runtime adds a
  **local SGD fine-tune** from Review Confirm/Discard labels, persisted in app
  files. No TFLite runtime, no model downloads — same guarantees: all on-device,
  works in airplane mode, classifier never supplies amounts.
- **Rigorous ML gate:** hold-out + adversarial evaluation (precision/recall,
  confusion matrix) must pass thresholds before the SMS node merges — see
  `design/ML_REPORT.md`.
- **Git:** `main` + `feat/<node>-<slug>` branches, Conventional Commits, tags
  `v0.x.y-<slug>` (see `CONTRIBUTING.md`).

---

## 0. Contract — do / don't

**DO**
- Follow the chosen design's tokens exactly (`design-tokens.json` → Compose mapping in §9).
- Store every money value as `Long` minor units (paise/cents). Never `Float`/`Double`.
- Store every SMS decision in the `sms` table (audit trail), even "ignored".
- Write parser unit tests from the corpus **before** wiring the parser to UI (M2 gate).
- Build and run in the emulator using §10 after every milestone.

**DON'T**
- **Never add the INTERNET permission.** The app is offline-only by design.
- Never auto-record an SMS below the confidence threshold (default 85).
- Never use the system soft keyboard for money amounts (custom keypad only).
- Never send any data off-device (no analytics, no crash reporting SDKs).
- Don't add DI frameworks, image libraries, or anything not listed in §2 — keep the dependency tree small.

## 1. What the app is

A **local-only** Android app that:
1. On first run asks for the user's **exact current balance** (to the last digit) per account.
2. Scans the device's SMS inbox on-device, detects bank transactions
   (debits/credits), and records them automatically or via a review queue.
3. Maintains per-account running balances + a combined total, with manual
   entry, transfers, and reconciliation ("bank says X").

**Acceptance criteria (the definition of done)**
- [ ] First run: currency → account (name, type, a/c tail) → exact balance keypad → permission rationale → Home. Cannot skip the exact balance.
- [ ] Balance always equals `opening + Σcredits − Σdebits + adjustments`, displayed to the last digit.
- [ ] Whitelisted bank SMS with confidence ≥85 → auto-recorded (with ref + source SMS link).
- [ ] Confidence 50–84 or unmapped sender → Review queue; Confirm/Edit/Discard; never a silent wrong write.
- [ ] OTP/promo/balance-only SMS → ignored, stored for audit.
- [ ] Multi-account: per-account balances, "All accounts" total, SMS→account auto-mapping by a/c tail + manual sender rules, transfers as linked pairs.
- [ ] Reconcile: typing the true balance creates a single ADJUSTMENT entry.
- [ ] Works with zero network access (verify merged manifest has no INTERNET).
- [ ] All 16 corpus cases in `sms-parsing-playbook.md` §7 pass as unit tests.

## 2. Stack (verified for this machine — Nov 2026)

| Item | Value |
|---|---|
| Language | Kotlin 2.0.x |
| UI | Jetpack Compose + **Material 3**, single Activity, Navigation Compose |
| DB | Room 2.6.x (KSP) |
| Settings | DataStore Preferences |
| Background | WorkManager (backfill only) + manifest BroadcastReceiver (live SMS) |
| JDK | 21 (installed at `/usr/bin/java` ✓) |
| Android SDK | `/opt/homebrew/share/android-commandlinetools` — SDK 36, build-tools 36.0.0, google_apis image ✓ |
| Gradle | CLI not installed → `brew install gradle` once, then use the wrapper |
| minSdk / target / compile | 26 / 35 / 36 |
| Package | `app.pocketledger` |
| Modules | single `:app` module — keep it simple |
| Fonts | chosen design's TTFs bundled in `res/font` (download once from Google Fonts GitHub; not flutter/no internet-at-runtime) |

Dependencies (only these): `compose-bom`, `material3`, `navigation-compose`,
`lifecycle-viewmodel-compose`, `room-runtime/ktx/compiler`, `datastore-preferences`,
`work-runtime-ktx`, `kotlinx-coroutines-android`, JUnit + Robolectric for tests.
No Hilt (use a manual `AppContainer` in `Application`), no Retrofit, no Firebase.

## 3. Project scaffold

```bash
cd ~/code/pocketledger
gradle wrapper --gradle-version 8.10.2        # after: brew install gradle
# create: settings.gradle.kts, gradle/libs.versions.toml, app/build.gradle.kts
# Kotlin sources under app/src/main/java/app/pocketledger/...
echo "sdk.dir=/opt/homebrew/share/android-commandlinetools" > local.properties
./gradlew :app:assembleDebug                   # first build sanity check
```

`app/src/main/AndroidManifest.xml` — permissions (the whole point):

```xml
<uses-permission android:name="android.permission.READ_SMS"/>
<uses-permission android:name="android.permission.RECEIVE_SMS"/>
<!-- deliberately NO android.permission.INTERNET — assert this in CI/tests -->
<application android:name=".App" ...>
  <receiver android:name=".sms.SmsReceiver" android:exported="true"
            android:permission="android.permission.BROADCAST_SMS">
    <intent-filter android:priority="999">
      <action android:name="android.provider.Telephony.SMS_RECEIVED"/>
    </intent-filter>
  </receiver>
</application>
```

## 4. Architecture

```
app.pocketledger/
├── App.kti                  # AppContainer: db, repos, parser, settings
├── MainActivity.kti         # single activity + NavHost + bottom nav (4 tabs)
├── data/                    # Room entities, DAOs, repositories, DataStore
├── sms/                     # SmsReceiver, InboxBackfill, SmsParser (pure Kotlin), Confidence
├── domain/                  # balance math, reconcile, transfer pairing (pure Kotlin)
└── ui/                      # theme/ (from tokens), screens/, components/, keypad/
```

- MVVM: `StateFlow` from repositories → `ViewModel` → composables. Unidirectional.
- `SmsBus`: `MutableSharedFlow<SmsMessage>` singleton that `SmsReceiver` emits
  into; a collector in `App` pipes messages into the pipeline (receiver can't hold Android entry-points).
- All DB access via repositories; UI never touches DAOs directly.

## 5. Data model (Room)

```kotlin
@Entity(tableName = "accounts")
data class Account(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val type: String,                    // CHECKING | SAVINGS | WALLET | CREDIT | CASH
  val currency: String,                // "INR" default
  val openingBalanceMinor: Long,       // exact, from onboarding keypad
  val tail: String?,                   // "1234" — for SMS auto-mapping
  val sortOrder: Int = 0,
  val archived: Boolean = false,
  val createdAt: Long,
)

@Entity(tableName = "txns", indices = [Index("accountId"), Index("ts"), Index("ref")])
data class Txn(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val accountId: Long,
  val direction: String,               // DEBIT | CREDIT
  val amountMinor: Long,
  val currency: String,
  val ts: Long,                        // SMS timestamp or manual
  val merchant: String?,               // parsed "at TESCO" / "to UPI/X"
  val note: String?,
  val category: String?,               // optional, tiny preset list
  val source: String,                  // MANUAL | SMS | IMPORT
  val kind: String = "TXN",            // TXN | ADJUST
  val ref: String?,                    // UTR/RRN/UPI ref
  val transferId: String?,             // links a transfer pair (one DEBIT + one CREDIT)
  val confidence: Int? = null,         // SMS confidence when source=SMS
  val smsId: Long? = null,             // FK to the raw message
)

@Entity(tableName = "sms", indices = [Index(value = ["hash"], unique = true)])
data class SmsRaw(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val sender: String, val senderNorm: String, val body: String,
  val ts: Long, val hash: String,      // sha256(sender|body|ts)
  val status: String,                  // PENDING | PROCESSED | REVIEW | IGNORED
  val extractedJson: String?,          // parser output snapshot
)

@Entity(tableName = "sender_rules")
data class SenderRule(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val pattern: String,                 // contains-match, e.g. "HDFC"
  val accountId: Long?,
  val autoRecord: Boolean = true,
  val createdAt: Long,
)
```

Balance query (single source of truth — put it in `TxnDao`):

```sql
SELECT a.opening_balance_minor
     + COALESCE(SUM(CASE WHEN t.direction = 'CREDIT' THEN t.amount_minor
                         ELSE -t.amount_minor END), 0)
FROM accounts a
LEFT JOIN txns t ON t.account_id = a.id AND t.kind = 'TXN'
WHERE a.id = :accountId
```

## 6. Money rules

1. **Minor units everywhere.** `₹1,234.50` → `123450L`. Formatting only at the UI edge (`NumberFormat` with account currency; `en-IN` → lakh grouping).
2. **Keypad.** Digits + `.` + backspace; live-formatted; max 2 decimals; ≥48dp keys; disabled CTA until a valid amount.
3. **Transfers** = two `TXN`s sharing a `transferId` (DEBIT in A + CREDIT in B).
   Counted in account balances; **excluded** from in/out summaries (filter `transferId IS NULL`).
4. **Reconcile** creates exactly one `kind = ADJUST` txn (signed difference,
   note "Reconcile dd MMM yyyy"). Multiple reconciles are fine; never edit history silently.
5. **Currency mismatch** (e.g. USD charge on INR account): record original
   currency + amount, flag for review, keep the account in its own currency. No conversion without an explicit user rate.

## 7. SMS engine (M2 — the core feature)

Follow `sms-parsing-playbook.md` §1–§8 exactly (pipeline, sender formats, junk
filter, regexes, confidence scoring, dedup). Implementation notes:

- **Backfill on first grant:** query `Telephony.Sms.Inbox` (`content://sms/inbox`,
  `date DESC`, cap 5000) in a coroutine with progress UI ("Scanned x / y");
  mark `backfill_done_at` in DataStore so it runs once.
- **Live SMS:** `SmsReceiver.onReceive` → `goAsync()` → emit into `SmsBus` →
  pipeline runs on IO. Parser itself is a pure object: `ParsedCandidate.parse(sender, body, ts)`.
- **Sender whitelist = patterns** (contains/regex, e.g. `HDFC`, `ICICI`, `MONZO`,
  `CHASE`) — NOT exact sender equality (emulator injects numeric senders; India
  prefixes vary by carrier).
- **Auto-mapping to accounts:** parsed a/c tail → account with that tail; else
  sender rule → account; else REVIEW with "assign to account" action (creates a `SenderRule`).
- **Review queue:** stored candidates with status REVIEW; Confirm writes the
  `Txn` + marks PROCESSED; Edit opens pre-filled entry screen; Discard marks IGNORED.
- **Thresholds in Settings:** auto ≥85, review ≥50 (defaults).
- Optional: local notification on auto-record ("Recorded −₹12.40 · Tesco").

## 8. Screens & flows

| Screen | Purpose | Wireframe |
|---|---|---|
| Splash → Permissions rationale | why SMS, "data never leaves this phone" | DESIGNS.md shared UX |
| Onboarding: currency → account → **exact balance keypad** | the front door | S1 |
| Home (tab 1) | balance hero + day-grouped ledger + scan chip + FAB | S2 |
| Accounts (tab 2) | account cards, add/edit, sender rules | S3 |
| Review (tab 3) | parsed candidates queue | S4 |
| Settings (tab 4) | theme/design, thresholds, currency default, export, lock, danger zone | — |
| Entry (add/edit txn) | keypad, direction toggle, merchant/note, date | — |
| Reconcile | exact-balance adjustment flow | S5 |
| Search/Activity | filters: account, direction, date range, amount range, source | — |
| Txn detail | fields + "view original SMS" | — |

Flows: first-run chain (S1 order) · SMS → parse → auto-record or Review ·
transfer create (2 linked txns) · reconcile (S5) · export via
`ACTION_CREATE_DOCUMENT` (JSON + CSV).

## 9. Theming (tokens → Material 3)

Map `design-tokens.json` → Compose (per chosen design `designs[N]`):

| Token role | Material 3 |
|---|---|
| background / onBackground | `lightColorScheme(background=, onBackground=)` |
| surface / onSurface / surfaceVariant / onSurfaceVariant / surfaceContainer | same-named scheme slots |
| primary / onPrimary / primaryContainer / onPrimaryContainer | same-named slots |
| secondaryContainer / onSecondaryContainer | same-named slots |
| outline / outlineVariant / error | same-named slots |
| **positive / negative / pending** (custom) | `LocalAppColors` CompositionLocal extension |
| typography.scale | `Typography()`: `displayLarge` ← balanceHero, `headlineSmall` ← headline, `titleMedium` ← title, `bodyMedium` ← body, `labelSmall` ← label; amounts get `fontFeatureSettings="tnum"` |
| shape.card/sheet/chip/button/key | `MaterialTheme.shapes`: medium=card, large=sheet, extraSmall=key, small=button; chips via `RoundedCornerShape(percent=50)` when chip=999 |
| elevation | per design: D1/D3 none (1dp borders), D2 soft shadow, D4 paper shadow |

- Fonts: bundle TTFs in `res/font`, build `FontFamily(R.font.inter_regular, …)`.
- Dark theme from the design's `dark` block (D3: dark is default, light available).
- Optional M4 stretch: ship all four designs as switchable themes (fonts differ per design).

## 10. Run it in the emulator (exact commands)

```bash
export ANDROID_SDK_ROOT=/opt/homebrew/share/android-commandlinetools
export PATH="$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/emulator:$PATH"
cd ~/code/pocketledger

# AVD — a google_apis image is installed; reuse existing groove_pixel if preferred
avdmanager list avd                                        # existing: groove_pixel (aosp_atd)
avdmanager create avd -n pocket_pixel -k "system-images;android-36;google_apis;arm64-v8a" -d pixel_7

emulator -avd pocket_pixel -no-snapshot-load &             # or: emulator -avd groove_pixel
adb wait-for-device

./gradlew :app:installDebug
adb shell pm grant app.pocketledger android.permission.READ_SMS
adb shell pm grant app.pocketledger android.permission.RECEIVE_SMS
adb shell am start -n app.pocketledger/.MainActivity

# Inject bank SMS (full corpus in sms-parsing-playbook.md §8):
adb emu sms send HDFCBK "Rs.1,234.50 debited from a/c XX1234 on 19-11-26 to UPI/ANIKET/YESB. Avl Bal Rs.45,678.90"
adb emu sms send ICICIB "Rs.25,000.00 credited to a/c XX5678 towards NEFT CR-ACME CORP Ref 045123ABC"
adb emu sms send MONZO   "You spent £12.40 at Tesco. Balance: £1,234.56"

adb logcat -s PocketLedger                                 # app log tag during debugging
```

Note: `adb emu` requires the emulator to be running; sender arrives as a
numeric address — this is exactly why the whitelist matches by pattern (§7).

## 11. Tests

- **Parser (the critical suite):** table-driven JUnit over all 16 corpus rows
  in `sms-parsing-playbook.md` §7 — assert direction, amountMinor, tail, ref, status. Gate for M2.
- **Balance math:** opening + Σ; adjustments; transfer pair excluded from
  summaries; penny-exact `Long` arithmetic (e.g. `123450 + 2500000 - 1240`).
- **DAO:** Room in-memory DB — balance query, dedup unique index, rule lookup.
- Run: `./gradlew testDebugUnitTest`. Optional: `connectedDebugAndroidTest` smoke test.

## 12. Milestones

| M | Scope | Definition of done |
|---|---|---|
| **M0** | Scaffold + theme | Builds via wrapper; installs on emulator; themed shell with 4 empty tabs; chosen fonts render |
| **M1** | Manual ledger | Onboarding chain (currency/account+tail/exact balance keypad), Home, Accounts CRUD, manual entry, empty states; balance = formula to the paise |
| **M2** | SMS engine | Permission flow, backfill with progress, receiver, parser + rules, Review queue, auto-record ≥85, dedup; **all 16 corpus unit tests green** |
| **M3** | Money ops | Transfers, Reconcile, search/filters, Settings (thresholds, currency, export JSON/CSV), optional biometric lock + record notification |
| **M4** | Design polish | Motion, empty states, light+dark both correct, parser edge hardening; acceptance criteria §1 all checked |

## 13. Privacy & policy

- **No INTERNET permission — enforce it:** `aapt2 dump badging app-release.apk | grep uses-permission` must not list INTERNET. Add a unit test asserting `Manifest.permission.INTERNET` never appears in the merged manifest.
- `READ_SMS`/`RECEIVE_SMS` are Google Play–restricted permissions: for **personal use, install the debug APK directly** (sideload/emulator = fine). Publishing to Play later would require default-SMS-handler status or a different ingestion path — out of scope.
- Export via SAF (`ACTION_CREATE_DOCUMENT`) only. Optional biometric lock via `BiometricPrompt`. All parsing on-device, no third-party SDKs.

## 14. Backlog (v2 — do not start until M4)

- `NotificationListenerService` for bank-app push notifications (UK/US banks often skip SMS).
- Hindi/Hinglish keyword pack (`rakha gaya`, `prapt hua`) — keyword tables are the extension point.
- Auto-pair transfers from matching debit+credit SMS within a 10-minute window.
- Home-screen widget with today's balance.
