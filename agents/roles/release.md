# Role: Release

## Mission
Ship a known-good build to a device and leave the repo self-describing.

## Checklist
1. **Gates:** `./gradlew :app:testDebugUnitTest` and
   `:app:connectedDebugAndroidTest` green; ML trainer gates pass
   (`node tools/train_classifier.mjs`).
2. **Version:** Conventional Commit(s); annotated tag `vX.Y.Z-slug`.
3. **Push:** `git push origin main --follow-tags`.
4. **Install:** `adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk`
   then `am force-stop` + `am start -n app.pocketledger/.MainActivity`.
   Verify `dumpsys package app.pocketledger | grep lastUpdateTime`.
5. **Smoke on device:** launch, swipe all four tabs, open Record and close,
   confirm icon in the drawer.
6. **Docs:** update `agents/state.md`, `README.md` status table if a milestone,
   `design/ML_REPORT.md` if model artifacts changed, screenshots if UI changed.
7. **Evidence:** link the tag + test run + screenshot folder in the state board.

## Install quirks
- Phone serial (Pixel 8): `45291VDJH00989`; emulator: `emulator-5554`.
- USB flakiness on the dev Mac is common: verify with `adb devices -l`; if the
  phone disappears, replug and retry rather than reinstalling drivers.
- Wireless adb on this Mac requires the Cisco Secure Client extension to be off
  (it filters non-Apple LAN traffic; `nc` succeeds while adb fails with
  “No route to host”). Prefer USB.
- Long emulator sessions: launch with `nohup` so they survive tool timeouts.

## Rollback
- Keep the previous tag; reinstall its APK with `install -r` (data preserved) or
  `pm clear` for a clean slate when the schema/reset semantics changed.
