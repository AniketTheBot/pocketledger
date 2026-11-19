# Contributing — PocketLedger

## Branches

| Branch | Purpose |
|---|---|
| `main` | Always buildable + tests green. Protected by convention (no direct pushes once CI exists). |
| `feat/<node>-<slug>` | One graph node or feature. e.g. `feat/n1-scaffold`, `feat/n6-sms-engine` |
| `fix/<slug>` | Bug fixes (`fix/review-queue-double-write`) |
| `docs/<slug>` | Documentation-only changes |
| `test/<slug>` | Test-only changes |
| `chore/<slug>` | Tooling, deps, housekeeping |

Flow: branch from `main` → commit → run gates → merge back with `git merge --no-ff`.
Release tags: `v<major>.<minor>.<patch>-<slug>` (e.g. `v0.2.0-slice`).

## Commit messages — Conventional Commits

```
type(scope): imperative summary <= 72 chars

optional body: what changed and why
```

Types: `feat` · `fix` · `test` · `docs` · `refactor` · `chore` · `perf`
Scopes: `scaffold` · `theme` · `data` · `money` · `sms` · `ml` · `ui` · `onboarding` · `home` · `accounts` · `review` · `settings` · `nav` · `ci` · `repo`

Examples:
- `feat(scaffold): gradle android project with compose`
- `test(sms): 16-case parser corpus`
- `fix(money): EU decimal parsing for comma formats`

## Gates before merging

1. `./gradlew :app:testDebugUnitTest` — all unit tests green.
2. `./gradlew :app:assembleDebug` — build succeeds.
3. UI branches: install + smoke test on emulator (`n8` E2E for the full checklist).
4. No `android.permission.INTERNET` in the merged manifest — ever.

## Rules

- Never commit `local.properties`, keystores, or machine-specific paths.
- Money is `Long` minor units. Parsers/formatters have tests. No floats.
- The ML classifier may classify; only deterministic extractors may produce amounts.
