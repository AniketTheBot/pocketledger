# Requirements — PocketLedger

Source of intent for every agent. When a request is ambiguous, this file plus
`design/HANDOFF.md` and `design/DESIGNS.md` decide. Update this file **before**
implementing a behavioral change; tests reference requirement ids.

## Product

A pocket money ledger for one person. It reads **bank SMS on the device**,
records money in/out automatically when confident, and always shows the exact
balance the user typed plus every subsequent movement. Offline by design.

- Persona: privacy-minded phone user in India/UK/EU with bank SMS alerts.
- Job to be done: “I want my real balance without typing every transaction.”
- Non-goals: bank API integrations, cloud sync, multi-user sharing, budgets 2.0.

## Hard rules

1. Offline only; **no INTERNET permission**; works in airplane mode.
2. Money is stored as `Long` minor units; display is exact (no rounding).
3. Deterministic parser extracts amounts/direction; the ML model only classifies.
4. Confidence routing: ≥ auto threshold (default 85) records; below → Review;
   junk (OTP/promo/balance-only) → Ignored.
5. Every recorded SMS transaction is traceable to its original message.
6. Onboarding must start from a clean state after an erase (no leftovers).

## Flows (acceptance level)

### F1 — Onboarding
Welcome → name → currency → first account → exact balance (custom keypad) →
SMS permission (allow or skip) → optional learning scan.
Acceptance: finishing creates **exactly one** account; hello uses the name;
skipping permission still lands on Home.

### F2 — Ledger
Home shows balance, in/out for the month, recent transactions; entry via keypad
(Record) incl. transfers; entry rows are tappable → delete with confirm.
Acceptance: save updates balance immediately; delete restores it; row disappears.

### F3 — SMS ingestion
Receiver + history scan (learning-only during onboarding) → parse → classify →
route. Low confidence → Review; Confirm books it, Discard teaches NOT_TXN.
Acceptance: a seeded HDFC-style debit SMS above threshold records automatically;
an OTP message never does; ambiguous messages wait in Review.

### F4 — Review queue
Cards show message, amount, direction, edits; Confirm/Discard; bulk via rules
(“always record from sender”). Acceptance: queue empties on action; totals update.

### F5 — Accounts
Multiple accounts/currencies, tail-based SMS routing, archive with restore,
per-account balances. Acceptance: archived accounts disappear from pickers and
totals but keep history and can be restored.

### F6 — Analyze & reconcile
Smart Analyzer (period, account, money out/in, pulse, balance trail, merchants),
Reconcile (typed bank balance → single adjustment). Acceptance: adjustment
changes balance to the typed value exactly.

### F7 — Settings, export, erase
Thresholds, scan now, rules list, JSON/CSV export via SAF, and **Erase
everything**: clears DB, prefs, learned model, corpus, caches, then relaunches
into onboarding. Acceptance: after erase, account/txn/sms tables are empty,
`onboarded=false`, and onboarding starts from Welcome — no duplicate accounts.

### F8 — Widget & welcome
Balance widget on the launcher; animated full-screen welcome with its own
illustration and CTA. Acceptance: widget reflects the active account; welcome
plays its entrance then idles; CTA carries the app theme (mustard pill).

## Cross-cutting

- Navigation: Home / Accounts / Review / Settings as a horizontal pager — pages
  follow the finger, and the nav pill highlight tracks the drag.
- Reduce surprise: every destructive action confirms and is reversible except
  the explicit erase.
- Accessibility: 48dp targets, `sp` text, AA contrast in the cream theme.

## Where requirements live

| Topic | File |
|---|---|
| Design language & screens | `design/DESIGNS.md`, `design/HANDOFF.md`, `design-tokens.json` |
| SMS grammar/golden rows | `design/sms-parsing-playbook.md` |
| Model, corpus, gates | `design/ML_REPORT.md` |
| Status & next tasks | `agents/state.md` |
