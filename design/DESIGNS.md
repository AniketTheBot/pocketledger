# PocketLedger — 4 design directions

> Source of truth for look & feel. Machine-readable tokens: `design-tokens.json`.
> Exact static renders of every screen: `mockups.html` (open in a browser).
> Concept art: `mockups/*.png`.

---

## Pick one

| # | Name | One-line vibe | Choose if you want… |
|---|---|---|---|
| **1** | **Quiet Ledger** | paper-and-ink passbook | maximum calm, zero chrome |
| **2** | **Nordic Mint** | soft friendly fintech | rounded cards, fresh teal |
| **3** | **Midnight Terminal** | dark-first power tool | mono digits, scan status lines |
| **4** | **Warm Cashbook** | cozy money diary | serif headings, cream paper |

Tell the implementing agent one line: **"Implement Design N per HANDOFF.md."**
Every design ships light **and** dark (Design 3 is dark-first).

---

## Shared UX laws (identical in all four designs)

1. **Penny-exact entry.** Money is typed on a custom keypad — digits, decimal
   point, backspace. No system keyboard. The amount formats live
   (`₹24,861.15`, lakh grouping for `en-IN`). Onboarding cannot continue until
   the exact balance is entered.
2. **Balance formula.** `balance = opening + Σcredits − Σdebits + adjustments`,
   always shown to the last digit, never rounded.
3. **Money is Long minor units** (paise/cents). Never `Float`/`Double`.
4. **No silent wrong writes.** SMS below confidence threshold lands in a
   Review queue (Confirm / Edit / Discard). Only ≥85 auto-records.
5. **Offline-only.** No INTERNET permission exists. Export = local file share.
6. **Traceability.** Every recorded transaction can show its original SMS.
7. **48dp touch targets**, WCAG AA contrast in light and dark, `sp` text, custom
   keypad keys ≥48dp.

## Shared screens (wireframes)

### S1 · Onboarding — exact balance (the app's front door)

```
┌────────────────────────────────┐
│ 9:41                       ▂▄▆ │
│  STEP 3 OF 3 · BALANCE         │
│                                │
│  What's your balance           │
│  right now?                    │
│  Exact to the last digit —     │
│  you can correct it any time   │
│  with Reconcile.               │
│                                │
│        ₹ 24,861.1▏             │
│        Everyday · checking     │
│                                │
│  ┌─────┐ ┌─────┐ ┌─────┐       │
│  │  1  │ │  2  │ │  3  │       │
│  └─────┘ └─────┘ └─────┘       │
│  ┌─────┐ ┌─────┐ ┌─────┐       │
│  │  4  │ │  5  │ │  6  │       │
│  └─────┘ └─────┘ └─────┘       │
│  ┌─────┐ ┌─────┐ ┌─────┐       │
│  │  7  │ │  8  │ │  9  │       │
│  └─────┘ └─────┘ └─────┘       │
│  ┌─────┐ ┌─────┐ ┌─────┐       │
│  │  .  │ │  0  │ │  ⌫  │       │
│  └─────┘ └─────┘ └─────┘       │
│  [        Start tracking     ] │
└────────────────────────────────┘
```
Flow: (1) currency → (2) first account: name, type, last 3–4 digits of a/c →
(3) this screen: exact current balance → SMS permission rationale → Home.

### S2 · Home — balance + ledger

```
┌────────────────────────────────┐
│ 9:41                       ▂▄▆ │
│  PocketLedger               ⚙  │
│  [Everyday ••1234 ▾]  [All]    │
│  Last scan 09:41 · 3 new       │
│ ┌────────────────────────────┐ │
│ │ AVAILABLE BALANCE          │ │
│ │ ₹24,861.15                 │ │
│ │ as of today, 09:41         │ │
│ │ ↓ out ₹412.30  ↑ in ₹25,000│ │
│ └────────────────────────────┘ │
│  TODAY                         │
│  ● Coffee · Tesco     −₹12.40  │
│  ● Salary · Acme     +₹25,000  │
│  YESTERDAY                     │
│  ● Groceries          −₹56.80  │
│  ● Streaming         −₹149.00  │
│                          ( ＋ ) │
│  [⌂]   [wallet]   [inbox]  [⚙] │
└────────────────────────────────┘
```
Day-grouped rows. Tap a row → detail (amount, merchant, account, ref, source
SMS preview). FAB → manual entry. Switcher: single account ↔ "All accounts".

### S3 · Accounts

```
┌────────────────────────────────┐
│  Accounts                   ⚙  │
│  All accounts                  │
│  ₹1,17,461.15 total            │
│ ┌────────────────────────────┐ │
│ │ Everyday · checking        │ │
│ │ ••1234 · from SMS (HDFC)   │ │
│ │ ₹24,861.15                 │ │
│ ├────────────────────────────┤ │
│ │ Savings · savings          │ │
│ │ ••7788 · from SMS (SBI)    │ │
│ │ ₹91,200.00                 │ │
│ ├────────────────────────────┤ │
│ │ Cash wallet · cash         │ │
│ │ manual only                │ │
│ │ ₹1,400.00                  │ │
│ └────────────────────────────┘ │
│  ＋ Add account                 │
│  Sender rules: HDFC→Everyday…  │
└────────────────────────────────┘
```
Account fields: name, type (checking/savings/wallet/credit/cash), currency,
opening balance (exact), a/c tail (3–4 digits) for SMS auto-mapping.

### S4 · Review queue — the safety valve

```
┌────────────────────────────────┐
│  Review · 2 waiting            │
│  [Waiting 2] [Today] [Ignored] │
│ ┌────────────────────────────┐ │
│ │ HDFCBK · TODAY 09:41       │ │
│ │ "Rs.1,234.50 debited from  │ │
│ │  a/c XX1234 … Avl Bal      │ │
│ │  Rs.45,678.90"             │ │
│ │ ── extracted ──            │ │
│ │ Direction   Debit          │ │
│ │ Amount      ₹1,234.50      │ │
│ │ To account  Everyday ••1234│ │
│ │ Ref         UPI/432198     │ │
│ │ Confidence  92             │ │
│ │ [Discard] [Edit] [Confirm] │ │
│ └────────────────────────────┘ │
└────────────────────────────────┘
```

### S5 · Reconcile — trust but verify

```
┌────────────────────────────────┐
│  Reconcile · Everyday          │
│                                │
│  App says:      ₹24,861.15     │
│  Bank says (type exactly):     │
│  ₹ 24,899.0▏                   │
│                                │
│  Difference:  +₹37.85          │
│  → will create one ADJUSTMENT  │
│    entry +₹37.85 · today       │
│                                │
│  [Cancel]      [Apply]         │
└────────────────────────────────┘
```

---

# Design 1 — Quiet Ledger

**Concept.** A passbook, not an app. Off-white paper, ink text, one ledger-green
accent, hairline rules instead of shadows. Everything breathes; numbers are the
hero. Feels like a private notebook you keep in your pocket.

**Palette** (`designs[0]` in `design-tokens.json`)

| Role | Light | Dark |
|---|---|---|
| background | `#FAF9F7` | `#121417` |
| surface | `#FFFFFF` | `#1B1E22` |
| ink / onSurface | `#191817` | `#ECEAE6` |
| muted / onSurfaceVariant | `#8A857D` | `#9BA1A6` |
| primary (ledger green) | `#1E6E4B` | `#7BCB9F` |
| positive | `#1E6E4B` | `#7BD3A0` |
| negative | `#B23A2E` | `#E08379` |
| outline (hairlines) | `#E8E4DE` | `#2A2E33` |

**Type.** Inter only; numerals tabular. Balance 34/600; labels 11 tracked caps.
**Shape.** Cards 12dp, sheets 20dp, buttons 12dp, keys 12dp. **Elevation: none** —
1dp hairline borders. **Motion:** fade + 8dp slide, 150ms, no bounce.

| Component | Treatment |
|---|---|
| Balance hero | hairline card, tracked-caps kicker, huge tabular figure, thin divider, in/out line |
| Txn row | flat list, hairline separators, glyph in soft square, green/red tabular amount |
| Bottom nav | text-first tabs, hairline top border, active = ink + underline tick |
| Keypad | bordered keys on paper, pressed = soft gray fill |
| Empty state | centered short line: "Nothing recorded yet. Messages stay on this phone." |
| Review card | paper card, quoted SMS in muted serif-ish italic? no — muted Inter, hairline box |

**Choose if** you want it to disappear into your pocket: quiet, trustworthy, timeless.

---

# Design 2 — Nordic Mint

**Concept.** Friendly fintech with soft edges. Pale blue-gray canvas, floating
white cards (radius 20, soft shadow), teal accent, pill chips everywhere,
springy motion, a progress ring while scanning.

**Palette** (`designs[1]`)

| Role | Light | Dark |
|---|---|---|
| background | `#F3F6F8` | `#0D1620` |
| surface | `#FFFFFF` | `#16222E` |
| ink | `#0E2439` | `#E6EEF5` |
| muted | `#6B7C8C` | `#8CA0B3` |
| primary (teal) | `#0FA3A3` | `#2EC4B6` |
| positive | `#0E9F6E` | `#45D6A4` |
| negative | `#E5484D` | `#FF6B6E` |
| outline | `#DCE4EA` | `#33465A` |

**Type.** Manrope; balance 32/750. **Shape.** Cards 20dp, sheets 28dp, chips pill,
keys 16dp. **Elevation:** soft shadow `0 8dp 20dp rgba(14,36,57,.08)` on cards.
**Motion:** spring 300ms (slight overshoot); balance counts up 600ms; list
stagger 30ms.

| Component | Treatment |
|---|---|
| Balance hero | white card, teal ring-dial (in/out split), count-up number |
| Txn row | rows inside one rounded white card, chip-like category tags |
| Scan chip | teal-tinted pill with progress ring: "Scanning · 3 new" |
| Bottom nav | floating pill bar, active = teal pill |
| Keypad | big rounded keys, CTA pill button |
| Review card | white card, colored Confidence meter bar |

**Choose if** you want approachable and modern — the "budgeting app your
friends would use" look.

---

# Design 3 — Midnight Terminal

**Concept.** Dark-first power tool. Near-black panels with thin outlines, neon
lime accent, monospace digits, and honest machine language: a status line that
reports the scan like a build log. The light theme is a "paper terminal"
(tokens included).

**Palette** (`designs[2]`, dark default)

| Role | Dark (default) | Light |
|---|---|---|
| background | `#0B0E14` | `#F6F7F5` |
| surface / panel | `#121721` | `#FFFFFF` |
| ink | `#D9E0EA` | `#171B22` |
| muted | `#6C7A8A` | `#5E6A77` |
| primary (neon lime) | `#9BE15D` | `#4F9A34` |
| positive | `#9BE15D` | `#4F9A34` |
| negative | `#FF6B6B` | `#C23B3B` |
| outline | `#2A3345` | `#D5DAD3` |

**Type.** Space Grotesk UI + **JetBrains Mono for every number and status line**.
**Shape.** Cards 8dp, keys 10dp — crisp, almost square. **Elevation: none**, 1dp
outlines; a 1px lime glow is allowed on the FAB only. **Motion:** near-instant
(100ms linear); status lines type out at 20ms/char; amounts never animate.

| Component | Treatment |
|---|---|
| Balance hero | panel with kicker `AVAIL_BALANCE`, mono figure, sub-line `as_of 09:41 · up_to_date` |
| Status line | `sms: 214 scanned · 37 matched · 2 queued` — mono, under the header |
| Txn row | table-like rows, `−` / `+` prefix on mono amounts, hairline grid |
| Bottom nav | terminal tabs, active = lime text + `▸` caret |
| Keypad | dark keys, lime key-press flash |
| Review card | raw SMS in mono quote block, extracted fields as key-value table |

**Choose if** you want it to feel like your own instrument — dense, honest, geek-calm.

---

# Design 4 — Warm Cashbook

**Concept.** A money diary on cream paper. Deep brown ink, terracotta accent,
soft serif display headings (Fraunces) over clean Inter body, ruled dividers
like a ledger notebook, and CREDIT/DEBIT "stamps" as sticker badges (tiny
2° rotation on confirm).

**Palette** (`designs[3]`)

| Role | Light | Dark |
|---|---|---|
| background | `#FFF8EF` | `#201A15` |
| surface | `#FFFDF8` | `#2A211A` |
| ink | `#3B2F26` | `#F2E8DC` |
| muted | `#9A8B7A` | `#A7937E` |
| primary (terracotta) | `#E07A5F` | `#E8906F` |
| positive | `#468B6B` | `#7FBF9A` |
| negative | `#C14E3E` | `#E08073` |
| outline | `#E3D5BE` | `#4A3C30` |

**Type.** Fraunces for headings + Inter body; numerals Inter tabular.
**Shape.** Cards 14dp, keys 14dp. **Elevation:** paper shadow
`0 4dp 10dp rgba(59,47,38,.06)` + ruled hairlines. **Motion:** 250ms ease-out;
day groups "unfold" 200ms.

| Component | Treatment |
|---|---|
| Balance hero | cream card, serif kicker "Today's balance", wax-dot accent |
| Txn row | diary line: merchant in ink, date in muted, stamped badge |
| Bottom nav | ink icons on paper, active = terracotta dot under tab |
| Keypad | paper keys, terracotta CTA "Write it down" |
| Review card | quoted letter style, terracotta underline on amounts |
| Empty state | "The ledger is blank. Your first entry is the balance you hold." |

**Choose if** you want warmth and character — the app as a keepsake, not a dashboard.

---

## Component matrix (quick diff)

| Component | D1 Quiet | D2 Mint | D3 Terminal | D4 Cashbook |
|---|---|---|---|---|
| Balance hero | hairline card, tabular | white card, count-up | mono figure + status line | serif kicker, cream card |
| Txn rows | hairline list | grouped white card | table rows, `−/+` mono | diary lines + stamps |
| Nav | underline tick | floating pill | terminal tabs | terracotta dot |
| Keypad | bordered paper keys | big rounded keys | dark keys, lime flash | paper keys |
| Scan feedback | quiet chip | progress ring pill | typed status line | "morning post" line |
| Elevation | none | soft shadow | none | paper shadow |
| Radius family | 12 | 20 | 8 | 14 |

## Icons & iconography

Outline icons, 24dp grid, 1.5–2dp stroke (D3: 1.5dp). Suggested set: Lucide /
Phosphor outlines exported to `VectorDrawable` — bundled, no icon web fonts.
Direction glyphs on amounts are typographic: `−` / `+` (D3 uses mono `-` / `+`).

## Dark mode & accessibility

- All four designs have complete light **and** dark token sets — build both; test both.
- Contrast is verified: body ≥4.5:1, balance/large ≥3:1 in every mode.
- All text in `sp`; honor system font scale; honor *remove animations* (drop
  springs/count-ups in D2/D4 when enabled).

## Compose implementation map

- `lightColorScheme`/`darkColorScheme` fed directly from token roles (mapping table in `HANDOFF.md` §9).
- Custom roles (`positive`, `negative`, `pending`) via a `CompositionLocal` extension.
- `balanceHero` type scale ← `themes[N].typography.scale.balanceHero`; numerals
  get `fontFeatureSettings = "tnum"`.
- Fonts: Archivo Black (display) bundled in `res/font`; body = system Roboto.

---

# ★ CHOSEN FOR BUILD — Layout E · Sticker Ledger (Theme 5)

Locked Nov 2026. This is the app. Implemented 1:1 from
`mockups/design-5-sticker-ledger.html` (kept as the visual reference; PNG
renders were removed on request).

**Structure.** Bottom navigation is a black pill bar with 4 items
(Home · Accounts · Review · Settings), active item = mustard pill with dark
icon+label. Home = greeting + avatar, balance StickerCard with two actions
(Record − / Scan SMS), accounts avatar row, Recent Transactions feed, floating
"+" FAB. Onboarding = welcome with the Figma sticker cast (human, wallet,
hand, coin, speech bubble) → currency → account → exact-balance keypad →
SMS permission (animated message + hand + coin) → scan summary.

**Theme tokens.** Background `#F6F3EC` · surface `#FFFDF7` · ink `#191614` ·
primary mustard `#F2C14E` · debit/pink `#F6AEDB` · credit/green `#A9E8A2` ·
pending/purple `#C9B8F5` · 2.5dp ink borders + solid 4dp×4dp hard shadows (0
blur) · radius 20 cards / 16 keys / 999 pills · display type Archivo Black ·
motion: one 180ms "sticker pop" on confirm, nothing else bouncy.

**Component inventory (Compose):** `StickerCard`, `PillButton`, `CandyChip`,
`MoneyKeypad`, `StickerArt` scenes (original Figma parts, animated), `BottomPillNav`,
`ScreenScaffold`, `AmountField`.

**Non-negotiables.** The bottom bar lives in `Scaffold.bottomBar` (never
floating over content); every screen scrolls under it safely; sticker art is
decorative only (never the sole carrier of information) and always comes from
the original Figma parts in `design/assets/welcome-svg` — no generated or
Canvas-drawn placeholder illustrations.
