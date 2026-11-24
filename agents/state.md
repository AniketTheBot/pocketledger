# Agent state board

Live memory of the project. Orchestrator owns it; every node appends evidence.
Statuses: `next` · `in-flight` · `blocked` · `done`.

## Done (recent milestones)

| Tag / version | Item | Evidence |
|---|---|---|
| `v0.9.0-welcome-choreography` | Animated welcome composition (6 Figma parts) | `docs/screenshots/welcome-live.mp4` |
| `v0.9.3-welcome-fullscreen` | Immersive full-screen welcome, themed CTA, shape bands | `docs/screenshots/welcome-dynamic.mp4` |
| `v0.9.4-ml-eval` | Corpus export + JVM ML eval gates | `MlEvalTest` green |
| `v0.9.5-nav-delete-fix` | Directional tab slide, row delete, archive, dialog fixes | E2E + manual |
| `v0.9.6-app-icon` | Launcher icon from `Icon.svg` | drawer screenshot |
| `v0.9.7-pager-wipe-icon` | Gesture pager + nav drag sync; full erase; icon sizing | unit build |
| `v0.9.8-automation` | E2E suite in `testing/`; erase runs in `appScope` (fixes leftover accounts) | 4/4 connected tests green |
| `v0.10.1-sticker-art` | Original Figma art replaces all Canvas flower placeholders; animated SMS-permission + All-clear scenes; `StickerArt.kt` reusable | `pixel8-sms-permission-art.png`, `pixel8-review-allclear-art.png`, `review-allclear-motion.mp4`, 4/4 connected |

## In flight

_(none)_

## Next (prioritised)

| # | Item | Owner | Acceptance | Evidence required |
|---|---|---|---|---|
| P1 | Real-corpus ML retrain + held-out eval | ml + qa | Export the full inbox with `tools/export_inbox.sh` (filtered/redacted by default), blend into `train_classifier.mjs`, regenerate `BundledModel.kt`, keep holdout/adversarial gates, add real-split metrics | `eval_report.json`, `MlEvalTest`, clean corpus JSONL |
| P2 | Manifest invariant as a JVM test | qa | Unit test fails if merged manifest gains INTERNET | test name in report |
| P3 | Pager feel tuning (300 ms settle, edge friction) | implementation | Swipe follows finger; highlight matches drag fraction; no jank on 60 Hz | screen recording + e2e swipe test |
| P4 | CI (GitHub Actions): unit + connected on AVD | release | PR runs both suites; ML gates block merge | workflow file + green run |
| P5 | Brand/logo pass (using `docs/screenshots/logo-reference/`) | design | Logo + icon variants approved; assets exported | `docs/` art |
| P6 | Review/Accounts/Archive E2E coverage | qa | New tests for F4/F5 in `testing/` | connected run |

## Open questions

- Real bank SMS corpus: exporter + cleaner are ready; blocked on the Pixel's USB connection → shapes P1.
- Whether to add Room migrations/export before v1 (architecture decision needed).

## Session log (newest first)

- **2026-11-24** — Added a one-command real-corpus pipeline:
  `DEBUG_EXPORT_INBOX` dumps the full device inbox without touching balances,
  `tools/export_inbox.sh` builds/installs, pulls over USB, and
  `tools/clean_corpus.py` keeps bank/financial + OTP negatives while redacting
  codes, names, phones, UPI ids, URLs and full card numbers; raw dumps are
  deleted from phone and Mac. Privacy regression tests green (7/7).
- **2026-11-24** — Replaced every hand-drawn flower sticker with the original
  Figma parts (wallet/coin/hand/human/speech bubble) through reusable animated
  `StickerArt` scenes: SMS permission now shows the message arriving into the
  hand + coin, Review empty state shows the coin dipping into the wallet. Dead
  onboarding flower code removed. 4/4 E2E green; permission/scan selectors
  hardened with a wait.
- **2026-11-24** — Built agent graph docs (`agents/`), automation suite verified
  4/4, erase root-caused (screen-scoped coroutine cancelled by onboarding swap)
  and fixed via `AppContainer.appScope`.
- **2026-11-22** — Welcome full-screen, icon from SVG, nav/delete/archive fixes.
