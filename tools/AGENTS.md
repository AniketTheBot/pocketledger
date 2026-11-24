# AGENTS.md — tools

Inherits the root contract (`../AGENTS.md`).

## ML trainer — `tools/train_classifier.mjs`
- Zero npm dependencies; deterministic seed **20261119**. Do not randomise.
- Outputs: `ml/BundledModel.kt` (weights/bias), `tools/eval_report.json`,
  `tools/eval_holdout.jsonl`, `tools/eval_adversarial.jsonl`.
- Gates (must not be lowered silently): holdout txn precision ≥ 0.99,
  recall ≥ 0.90, adversarial precision ≥ 0.95. Non-zero exit on regression.
- Any change to featurization (`ml/TextFeatures.kt`) must be mirrored in the
  trainer and the `feature_fixture` in `ClassifierTest` regenerated.
- Run: `node tools/train_classifier.mjs`; then `./gradlew :app:testDebugUnitTest`
  (MlEvalTest re-checks the same gates on the JVM).

## Corpus from a device (debug builds)

Full inbox (raw messages, no balance changes, no prior scan needed):
```bash
tools/export_inbox.sh
# -> tools/corpus/real/inbox-<ts>.jsonl (git-ignored)
```
or explicitly:
```bash
adb shell pm grant app.pocketledger android.permission.READ_SMS
adb shell am broadcast -n app.pocketledger/.DemoSeedReceiver -a app.pocketledger.DEBUG_EXPORT_INBOX
adb exec-out run-as app.pocketledger cat files/corpus/inbox-<ts>.jsonl > inbox.jsonl
```

App-processed corpus (labels + parser fields from the `sms` table):
```bash
adb shell am broadcast -n app.pocketledger/.DemoSeedReceiver -a app.pocketledger.DEBUG_EXPORT_CORPUS
adb exec-out run-as app.pocketledger cat files/corpus/<file>.jsonl > corpus.jsonl
```
Labels: `user_confirmed` / `user_discarded` are human; `ideal_label` is silver.

**Never commit raw real SMS.** `tools/corpus/real/` is git-ignored for exactly
this reason.

## Legacy emulator script
`tools/emulator_e2e.sh` — seeds the app, injects SMS via `adb emu sms send`,
screenshots, and asserts the merged manifest has **no INTERNET permission**.
Keep that assertion alive in any future CI.
