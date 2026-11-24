# Role: Architecture

## Mission
Keep the system shaped so requirements are cheap to satisfy and invariants are
hard to break. Approve dependency and schema changes.

## System map

```
SmsReceiver ─┐
InboxScanner ┼─▶ SmsPipeline.ingest/learnFromHistory
             │      ├─ SmsParser (deterministic fields; junks, direction, amount)
             │      ├─ HybridClassifier (bundled softmax + local SGD head)
             │      └─ routing → Room: sms / txns (PROCESSED | REVIEW | IGNORED)
             ▼
         Room (accounts, txns, sms, sender_rules)
             ▼
        UI (Compose) ← AppSettings (DataStore)
```

- Money math: `MoneyOps` (transfers, reconcile, manual entries, delete).
- Wallet queries live in `WalletRepository`; UI observes Flows.
- SMS engine modules: `sms/SmsParser.kt`, `sms/SmsPipeline.kt`,
  `sms/InboxScanner.kt`; ML in `ml/`.
- Compose shell: `MainActivity` (pager + nav), screens in `ui/screens/`,
  components in `ui/components/`, theme in `ui/theme/`.

## Standing decisions

| # | Decision | Rationale |
|---|---|---|
| A1 | Room + DataStore, no network | offline contract |
| A2 | Classifier over hashed char n-grams, Kotlin-compiled weights | no TFLite/runtime downloads |
| A3 | Local SGD head persisted in `filesDir/classifier_local.txt` | per-user adaptation without cloud |
| A4 | HorizontalPager for tabs; nav highlight = `currentPage + offsetFraction` | gesture-true navigation |
| A5 | Destructive resets run in `AppContainer.appScope` | survives onboarding swap / activity death |
| A6 | Compose Dialogs: content scrolls, actions inline (never pinned to window bottom) | Dialog windows can exceed the display |
| A7 | Adaptive icon: art ≤ ~46–60% of 108dp canvas, centered | circular masks clip edges |

## Change protocol
1. New dependency or schema → write the decision here first (table row + why).
2. Schema migrations require a plan (currently `version = 1`, `exportSchema=false`).
3. Keep ML and SMS parsing contracts in lockstep with `tools/train_classifier.mjs`
   (feature hashing, class order).
4. Enforce invariants in CI/tests, not comments.

## Open architecture tasks
- Manifest assertion for absence of INTERNET as a JVM test (currently only
  `tools/emulator_e2e.sh` checks it).
- Optional Room schema export + migration tests before v1.
