# AGENTS.md — app module

Inherits the root contract (`../AGENTS.md`). Local rules for anyone touching
`app/src`.

## Facts
- Package `app.pocketledger`; single Activity `MainActivity`; Compose only.
- Shell: `MainActivity` — `HorizontalPager` tabs, `BottomPillNav` drag-synced.
- Screens: `ui/screens/` (Home, AccountsReview, EntrySettings, Settings,
  Analyzer, Onboarding, WelcomeScreen). Widgets: `ui/components/Widgets.kt`.
- Data: `data/Db.kt` (Room), `data/Repos.kt` (wallet/settings/AppContainer),
  `domain/MoneyOps.kt` (money), `sms/*` (parser/pipeline/scanner), `ml/*`.
- Manifest: ❌ never add `android.permission.INTERNET`.

## Rules
1. New UI text must remain testable: keep stable, unique labels; SectionLabels
   render uppercase.
2. Add `Modifier.testTag(...)` for containers a test must gesture on (e.g.
   `tab_pager`).
3. Dialogs: actions inside the scrollable content, never pinned to the window
   bottom (window can exceed the screen).
4. Long-running/destructive work: `AppContainer.appScope`, not screen scopes.
5. Async lists: re-anchor selections via `LaunchedEffect(accounts)`.
6. After changing UI: run `./gradlew :app:assembleDebug` and update `testing/`
   flows when labels/structure change.

## Verify
```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```
