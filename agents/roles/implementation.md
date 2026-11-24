# Role: Implementation

## Mission
Ship the change described by the plan: correct code, Sticker design intact, tests
added, no invariant broken.

## Ground rules
- Kotlin + Compose only; no new dependency without the architecture role.
- Money = `Long` minor units. Read `Money.kt`/`MoneyOps.kt` before touching money.
- Every behavior change gets a test: `app/src/test` for logic, `testing/` for UI.
- No blocking work on the main thread; use `appScope`/`rememberCoroutineScope`
  appropriately (see A5).

## Lessons already paid for — do not repeat

1. **Destructive flows must outlive the screen.** Clearing DataStore flips Root to
   onboarding, cancelling screen-scoped coroutines. Use `AppContainer.appScope`
   (see Settings erase).
2. **Dialog windows can be taller than the display.** Pinning actions to the
   window bottom pushes them off-screen. Put actions inside the scrollable column
   (Entry/Reconcile dialogs). Keep keypads reachable.
3. **Async state races.** A dialog opened before accounts load must re-anchor its
   selection via `LaunchedEffect(accounts)` (otherwise Save stays disabled).
4. **Room Flow lag.** UI rows appear a frame or two after a DB write; tests must
   `waitUntil`, not assert immediately.
5. **adb broadcasts:** implicit debug broadcasts can be skipped by Android
   background policy — always send explicitly with
   `-n app.pocketledger/.DemoSeedReceiver`.
6. **Animations:** system animator scale 0 hides everything. Check
   `settings get global animator_duration_scale` before debugging “no animation”.
7. **Icons:** adaptive foreground lives in the 66/108 safe circle; keep the art
   ~46–60% of the canvas, centered, and verify with the circular preview.
8. **Pager + nav:** the pill highlight must interpolate from
   `pagerState.currentPage + currentPageOffsetFraction` (smoothstep), and page
   items must not consume horizontal drags.

## Where things live
| Change | File(s) |
|---|---|
| Home / rows / delete dialog | `ui/screens/Home.kt` |
| Accounts, review, add account | `ui/screens/AccountsReview.kt` |
| Entry/Reconcile/Scan dialogs | `ui/screens/EntrySettings.kt` |
| Settings, export, erase | `ui/screens/Settings.kt` |
| Tab pager & shell | `MainActivity.kt`, `ui/components/Widgets.kt` (BottomPillNav) |
| SMS parse/route | `sms/*`, `data/Db.kt` |
| ML | `ml/*`, `tools/train_classifier.mjs` |

## Done when
- `./gradlew :app:assembleDebug` and `:app:testDebugUnitTest` pass.
- UI change → `testing/` test updated/added; `connectedDebugAndroidTest` green
  on the emulator.
- Screenshots refreshed in `docs/` for visual changes.
- `agents/state.md` updated; Conventional Commit prepared.
