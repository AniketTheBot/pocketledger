# AGENTS.md — testing

Inherits the root contract (`../AGENTS.md`). This folder is the E2E suite
(the mobile equivalent of Playwright).

## Run
```bash
# emulator booted (headless fine): emulator -avd pocket_pixel8 -no-window &
./gradlew :app:connectedDebugAndroidTest
```
HTML report: `app/build/reports/androidTests/connected/`
Tests: `testing/androidTest/java/app/pocketledger/e2e/AppFlowTest.kt`
Wiring: `app/build.gradle.kts` maps this folder into the androidTest source set.

## Writing tests
1. Each test starts clean: `runBlocking { c.wipeEverything() }` + `scenario.recreate()`.
2. Seed fast paths via `wallet.addAccount` + `settings.setOnboarded(true)`; keep
   one full-onboarding test for the real flow.
3. Selectors: prefer exact text; `hasText(...) and hasClickAction()` when labels
   repeat; remember `SectionLabel` uppercases.
4. Wait for Room → Flow → UI: `rule.waitUntil { onAllNodesWithText(...)… }`.
5. Assert both UI and DB for writes; poll with `withTimeout` for async resets.
6. Never use `Thread.sleep`.

## When the app changes
- If a label or structure changes, update the flow in the same commit.
- A red suite blocks release (see `../agents/roles/release.md`).
