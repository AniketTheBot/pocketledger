# testing/

Automated **emulator E2E tests** for PocketLedger — the mobile equivalent of
Playwright. The app's real UI is driven on an emulator/device and the result is
verified both on screen and directly against the Room database.

## Run

```bash
# 1. boot an emulator (headless is fine)
$ANDROID_HOME/emulator/emulator -avd pocket_pixel8 -no-window -no-boot-anim &

# 2. run the whole suite
./gradlew :app:connectedDebugAndroidTest
```

HTML report: `app/build/reports/androidTests/connected/`

## Coverage

| Test | What it proves |
|---|---|
| `onboarding_createsExactlyOneAccount` | Full onboarding works and creates **exactly one** account (guards the regression where a bad wipe left the old account behind → duplicates). |
| `addEntry_thenDelete_removesItFromLedger` | Record → Save → row appears in the ledger; tap row → Delete → row and DB record are gone. |
| `pagerSwipe_movesThroughTabs` | HorizontalPager swipe reaches Accounts, Review and Settings (gesture nav, not just taps). |
| `eraseEverything_resetsDatabaseAndOnboarding` | Settings → Erase permanently clears accounts + prefs and returns to onboarding. |

## How isolation works

Every test starts from a clean device state: the suite calls
`AppContainer.wipeEverything()` (DB + DataStore + learned model + caches) and
recreates the activity before driving the UI. No test depends on another.

## Layout

```
testing/
  androidTest/java/app/pocketledger/e2e/AppFlowTest.kt   # the suite
  README.md                                              # this file
```

`app/build.gradle.kts` registers `testing/androidTest/java` as the androidTest
source dir and adds the Compose UI test + AndroidX test dependencies.
