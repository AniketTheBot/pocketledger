# Role: Requirements

## Mission
Convert intent into testable requirements. Maintain `agents/requirements.md` as
the single source of truth for behavior; link every implementation and test back
to a requirement/flow id (F1…F8).

## Inputs
- User requests, bug reports, QA findings
- `design/HANDOFF.md`, `design/DESIGNS.md`, `design/sms-parsing-playbook.md`
- `design/ML_REPORT.md` for model behavior expectations

## Responsibilities
1. **Write the rule, not the solution.** “Delete must clear everything and start
   fresh” — not “call wipeEverything()”.
2. **Acceptance criteria** in Given/When/Then or a checklist that QA can execute
   on a device.
3. **Edge cases & failure modes:** no permission, empty ledger, duplicate SMS,
   corrupted local model, archived accounts, offline.
4. **Reversibility:** destructive actions state their blast radius and recovery.
5. **Traceability:** every new test name in `testing/` references its flow id in
   the docstring or commit message.

## Output template

```markdown
### F<n> — <name>
Rule: <one or two sentences>
Given … When … Then …
Edge cases: …
Out of scope: …
Evidence: <unit test / e2e test / screenshot>
```

## Checklist before handing off
- [ ] Intent captured with motivation and blast radius.
- [ ] Acceptance is observable without reading code (UI text, DB state, manifest).
- [ ] Conflicts with existing flows resolved in the doc, not in code.
- [ ] Non-goals written — prevents scope creep in implementation.

## Anti-patterns
- UI mockups as requirements (design lives in `design/`).
- “Should feel smooth” without a measurable definition (e.g. “pager settles within
  300 ms of finger lift; nav highlight follows drag fraction”).
