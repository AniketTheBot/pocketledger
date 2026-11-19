# PocketLedger

A **local-only** Android money ledger. It reads your bank SMS **on the phone**,
recognises debits & credits with an on-device ML model, and keeps an exact,
account-aware balance — starting from the balance you type in, to the last
digit. No internet permission exists in the manifest. Nothing leaves the device.

**Design:** Layout E · Sticker Ledger (neo-brutalist: cream screens, ink
outlines, hard offset shadows, flower stickers, candy chips).

---

## Status

| Node | Scope | State |
|---|---|---|
| N0 | Design lock (Layout E + Sticker theme) | ✅ |
| N1 | Scaffold: Compose, theme, nav shell | ✅ |
| N2 | Data: Room schema, money math, repos | ✅ |
| N3 | SMS parser + corpus/adversarial tests | ✅ |
| N3-ML | Bundled classifier + local learning + eval | ✅ (`design/ML_REPORT.md`) |
| N4 | Onboarding (stickers → exact balance keypad) | ✅ |
| N5 | Home / Accounts / Entry | ✅ |
| N6 | SMS engine (receiver, backfill, review queue) | ✅ |
| N7 | Reconcile, transfers, settings, export | ✅ |
| N8 | Emulator E2E + SMS injection (Pixel 8) | ✅ |
| N9 | Polish + APK | ✅ debug APK built |

## Privacy invariants (enforced, not promised)

- **No `android.permission.INTERNET`** — asserted in the manifest and checked in E2E.
- Classifier + fonts are bundled; no runtime downloads; works in airplane mode.
- Export is the only data exit, and only on a user tap (SAF file save).
- ML classifies messages; **only deterministic extractors produce amounts**.

## Build & run

```bash
export ANDROID_SDK_ROOT=/opt/homebrew/share/android-commandlinetools
./gradlew :app:testDebugUnitTest     # gates
./gradlew :app:installDebug

# emulator (created for this project)
$ANDROID_SDK_ROOT/emulator/emulator -avd pocket_pixel &
$ANDROID_SDK_ROOT/platform-tools/adb shell am broadcast -a app.pocketledger.DEBUG_SEED
$ANDROID_SDK_ROOT/platform-tools/adb emu sms send HDFCBK "Rs.1,234.50 debited from a/c XX1234 on 19-11-26 to UPI/ANIKET/YESB. Avl Bal Rs.45,678.90"
```

Full script: `tools/emulator_e2e.sh` (installs, grants SMS permissions, seeds,
injects the fake-message corpus, captures screenshots).

## Layout

```
app/src/main/java/app/pocketledger/
├── money/        Money.kt — Long minor units, parse + format (en-IN)
├── data/         Room entities/DAOs, repositories, settings (DataStore)
├── sms/          parser, pipeline (fusion), receiver, inbox scanner
├── ml/           bundled classifier + local learner (subagent)
├── domain/       transfers, reconcile, manual entries
└── ui/
    ├── theme/    Sticker Ledger tokens
    ├── components StickerCard, PillButton, CandyChip, Keypad, FlowerSticker, PillNav
    └── screens/  onboarding, home, accounts, review, entry, reconcile, settings
```

Design source of truth: [`design/`](design/README.md) · conventions: [`CONTRIBUTING.md`](CONTRIBUTING.md).

## Branch model

`main` (always green) ← `feat/nX-…` branches merged with `--no-ff` after gates.
Conventional Commits; tags `v0.x.y-<slug>`.
