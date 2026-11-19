# PocketLedger — design package (handoff-ready)

**The app:** a 100% local Android app that reads your bank SMS messages on-device,
auto-records every debit & credit into a clean ledger, and keeps your exact
balance to the paise — across all your accounts. It asks for your current
balance (exact, to the last digit) before tracking starts. No internet. Ever.

**Status:** 🎨 Design complete — 4 directions, fully specced. Implementation not
started — that is the next agent's job, and this folder is its briefing.

---

## Where to look

| File | What it is |
|---|---|
| **`HANDOFF.md`** | 👉 AI agent: **START HERE.** Product spec, data model, SMS engine, exact emulator run-book, milestones M0–M4 |
| **`DESIGNS.md`** | The 4 design directions: palettes, typography, shapes, motion, screen-by-screen wireframes |
| **`design-tokens.json`** | Machine-readable tokens for all 4 designs (colors, type, radii) → maps straight into Flutter `ThemeData` |
| **`mockups.html`** | Open in a browser — 16 exact screen mockups (4 designs × 4 screens: setup, home, accounts, review) |
| **`mockups/*.png`** | Rendered 2× preview sheet per design (4 screens each) — generated from `mockups.html`; the HTML remains the source of truth |
| **`sms-parsing-playbook.md`** | SMS sender formats, regex patterns, confidence scoring, a ready-made test corpus with expected outputs |

## For humans
Open the mockups and pick a direction:

```
open /Users/aniketloona/code/pocketledger/design/mockups.html
```

Then tell the implementing agent one line:

> "Implement **Design N** per `HANDOFF.md`."

## For the implementing AI agent
1. Read `HANDOFF.md` top to bottom — it contains the full contract.
2. Read `DESIGNS.md` + `design-tokens.json` for the chosen design only.
3. Build per milestones M0 → M4; validate the parser against the corpus in
   `sms-parsing-playbook.md` before shipping the SMS feature.

## Environment (verified on this machine — Nov 2026)

| Item | Status |
|---|---|
| JDK | ✅ OpenJDK 21 (Temurin) — AGP-compatible |
| Android SDK | ✅ `/opt/homebrew/share/android-commandlinetools` (SDK 36, build-tools 36.0.0, google_apis image) |
| Emulator | ✅ binary present; existing AVD **`groove_pixel`** (Android 16) |
| Gradle | ⚠️ CLI not installed → `brew install gradle` once, then project wrapper (HANDOFF §3) |
| Kotlin stack | ✅ chosen: Kotlin + Jetpack Compose + Material3 (native SMS access) |
| Flutter | installed on this machine but **not used** for this app |
| Xcode / iOS | ❌ not needed (Android-only app) |

Exact emulator + SMS-injection commands are in `HANDOFF.md` §10.

## Open questions (decide before M0)

- **App name:** *PocketLedger* is a placeholder — rename freely (update `design-tokens.json` → `app.name`).
- **Default currency:** INR is suggested (this machine's locale is `en-IN`).
  Currency is a per-account setting regardless; "exact to the last digit" rule is currency-agnostic.
- **Design choice:** one of the 4 in `DESIGNS.md` (D3 *Midnight Terminal* is dark-first).
