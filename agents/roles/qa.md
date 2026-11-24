# Role: QA

## Mission
Prove behavior on a real Android runtime and catch regressions before release.
QA owns `testing/`, device evidence, and the regression matrix.

## Commands

```bash
export ANDROID_SDK_ROOT=/opt/homebrew/share/android-commandlinetools

./gradlew :app:testDebugUnitTest          # JVM: parser corpus, ML eval, money
./gradlew :app:connectedDebugAndroidTest  # E2E on emulator/device
node tools/train_classifier.mjs           # ML gates (CI-equivalent)
bash tools/emulator_e2e.sh                # legacy scripted smoke (SMS injection)
```

Emulator (headless ok): `emulator -avd pocket_pixel8 -no-window -no-boot-anim &`

## Current E2E suite (testing/androidTest/.../AppFlowTest.kt)

| Test | Guards |
|---|---|
| `onboarding_createsExactlyOneAccount` | F1 + duplicate-account regression after erase |
| `addEntry_thenDelete_removesItFromLedger` | F2 keypad save, row tap, delete, DB cleared |
| `pagerSwipe_movesThroughTabs` | gesture pager across Accounts/Review/Settings |
| `eraseEverything_resetsDatabaseAndOnboarding` | F7 full wipe + onboarding reset |

## Writing a new test
1. Add it to `testing/androidTest/java/app/pocketledger/e2e/`.
2. Start from clean state: `runBlocking { c.wipeEverything() }` + `scenario.recreate()`.
3. Prefer **exact** text; use `hasText(...) and hasClickAction()` when labels repeat.
4. Section labels render UPPERCASE (`SectionLabel` uppercases) — assert the shown text.
5. Wait for Room flows: `waitUntil { onAllNodesWithText(...).fetchSemanticsNodes().isNotEmpty() }`.
6. Assert both UI and DB for writes.
7. Screenshot evidence: `docs/screenshots/` (use `docs/screenshots/logo-reference` style naming).

## Regression matrix (feature → evidence)

| Flow | JVM | E2E | Other |
|---|---|---|---|
| F1 onboarding | — | onboarding_createsExactlyOneAccount | screenshot |
| F2 ledger/delete | Money tests | addEntry_thenDelete… | — |
| F3 SMS parse/route | SmsParserCorpusTest, ClassifierTest | — | emulator_e2e.sh SMS injection |
| F3 ML gates | MlEvalTest (holdout/adversarial) | — | train_classifier.mjs |
| F4 review | — | (add when touched) | manual screenshots |
| F5 accounts/archive | — | (add when touched) | — |
| F6 analyzer/reconcile | Money.reconcile | — | screenshots |
| F7 erase | — | eraseEverything… | DB pulled via run-as |
| F8 nav/welcome | — | pagerSwipe… | tab-slide.mp4 |
| Privacy | — | — | emulator_e2e.sh asserts no INTERNET |

## Reporting a failure
Include: test name, device, exact assertion text, screenshot/screen recording,
and the smallest repro steps. File it as a state-board entry with `blocked` or
`next` and hand it back to implementation.

## Anti-patterns
- “Works on my emulator” without the connected suite.
- `Thread.sleep` instead of `waitUntil`.
- Asserting implementation details (private state) instead of observable behavior.
