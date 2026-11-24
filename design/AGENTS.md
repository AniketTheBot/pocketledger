# AGENTS.md — design

Inherits the root contract (`../AGENTS.md`). Source of truth for look, feel,
and ML expectations.

## Documents
| File | Use |
|---|---|
| `HANDOFF.md` | locked decisions (stack, design, ML deviation), acceptance |
| `DESIGNS.md` | the four directions; chosen: Layout E · Sticker Ledger |
| `design-tokens.json` | palette/spacing/typography tokens |
| `ML_REPORT.md` | model, corpus, gates, artifact list |
| `sms-parsing-playbook.md` | SMS grammar + golden corpus rows |
| `assets/` | Figma exports (SVG parts with absolute bounds) |

## Rules
1. **Vectors only** for illustrations; no rasterisation of the Figma art.
2. Welcome frame: `WelcomeScreen` 271×502; six exported groups; keep the
   absolute-bounds placements unless requirements change.
3. Launcher icon: adaptive; art ~46–60% of the 108dp canvas, centered inside the
   66/108 safe circle; monochrome silhouette included.
4. When ML artifacts change, update `ML_REPORT.md` in the same change.
5. Figma file (read-only): key `1lBncqAOnwlVBPA9q5kTJk`; local token at
   `~/.config/figma_token` — never commit tokens.
