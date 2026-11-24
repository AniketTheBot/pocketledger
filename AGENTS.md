# AGENTS.md — PocketLedger

Offline-only Android money ledger. Bank SMS are classified **on device**; above a
confidence gate the transaction auto-records; balances are penny-exact. Kotlin +
Jetpack Compose, Sticker Ledger design.

## Non-negotiable invariants

1. **No `android.permission.INTERNET`, ever.** Enforced by code review + the
   manifest check in `tools/emulator_e2e.sh`. The app must work in airplane mode.
2. **Money is `Long` minor units** (paise/cents). Never `Float`/`Double` for money.
3. **The classifier never produces amounts.** Only the deterministic
   `SmsParser` extracts numbers; ML only classifies NOT_TXN / DEBIT / CREDIT.
4. **ML gates may not be lowered silently.** Thresholds and holdout/adversarial
   fixtures live in `design/ML_REPORT.md` + `tools/eval_report.json`; the JVM
   gate is `MlEvalTest`.
5. **Low confidence never writes silently** — it goes to Review
   (Confirm / Edit / Discard). Auto-record only at the configured threshold.
6. **UI = Sticker Ledger** (Layout E): cream canvas, ink outlines, hard offset
   shadows, candy chips. No new visual language without the design role.

## Agent workflow

This repo is built by role agents arranged as a graph — see
[`agents/README.md`](agents/README.md):

- `agents/roles/*.md` — role contracts (requirements, architecture, implementation, QA, release, orchestration)
- `agents/state.md` — live board: done / in flight / next with acceptance criteria
- `agents/requirements.md` — distilled product requirements and flows

**Before working:** read `agents/state.md` and the role(s) you are filling.
**After working:** update `agents/state.md` plus any affected docs. Changes to
behavior require a passing test or an explicit, documented exception.

## Build & verify

```bash
export ANDROID_SDK_ROOT=/opt/homebrew/share/android-commandlinetools

./gradlew :app:testDebugUnitTest            # JVM gates (parser, ML eval, units)
./gradlew :app:assembleDebug                # APK -> app/build/outputs/apk/debug/
./gradlew :app:connectedDebugAndroidTest    # E2E on emulator (testing/ suite)
node tools/train_classifier.mjs             # retrain + ML gates (writes BundledModel.kt)
```

E2E details: [`testing/README.md`](testing/README.md). Release checklist:
[`agents/roles/release.md`](agents/roles/release.md).

## Repo map

| Path | Contents |
|---|---|
| `app/src/main/java/app/pocketledger/` | UI (Compose), data (Room/DataStore), SMS engine, ML |
| `app/src/test/` | JVM unit tests (parser corpus, classifier fixtures, ML eval) |
| `testing/androidTest/` | Instrumented E2E suite (separate source set) |
| `tools/` | ML trainer, eval fixtures, emulator E2E script |
| `design/` | HANDOFF, DESIGNS, design tokens, ML report, SMS playbook |
| `docs/` | screenshots, logo references |

## Device & emulator facts

- Package: `app.pocketledger` · AVD: `pocket_pixel8` (1080×2400, API 36)
- Debug broadcasts (debug builds only), **always explicit**:
  `adb shell am broadcast -n app.pocketledger/.DemoSeedReceiver -a app.pocketledger.DEBUG_SEED`
  (same receiver: `DEBUG_SCAN`, `DEBUG_EXPORT_CORPUS`)
- Long emulator runs: launch with `nohup … &` (foreground calls get killed on timeout).
- Dev Mac has a Cisco socket filter that blocks non-Apple LAN traffic → prefer USB
  for adb; wireless debugging only works with that extension disabled.

## Change rules

- Conventional Commits; tags `vX.Y.Z-slug`; push `main` with `--follow-tags`.
- Every user-visible change ships with: test coverage (unit and/or
  `testing/`), updated screenshots if visual, and an `agents/state.md` update.
- New dependency = architecture role decision (documented in that role's log).
