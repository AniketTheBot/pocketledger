# SMS parsing playbook — PocketLedger

Everything the implementing agent needs to turn raw inbox SMS into ledger
entries **without ever recording a wrong transaction**.

---

## 1. Pipeline

```
Telephony.Sms.Inbox ──backfill on first run──▶
                                            │
BroadcastReceiver (SMS_RECEIVED) ───────────┤
                                            ▼
                            1. sender normalize (see §2)
                            2. whitelist filter (see §3)
                            3. junk filter: OTP / promo / balance-only → IGNORED
                            4. field extraction: amount, direction, a/c tail, ref, reported balance (see §4)
                            5. confidence score (see §5)
                            6. route:  ≥85 & sender mapped → AUTO-RECORD
                                       50–84                → REVIEW queue
                                       <50                  → IGNORED (kept for audit)
                            7. dedup by hash + (sender, amount, direction, ts±60s)
```

- Parser is pure Dart/Kotlin: `ParsedCandidate parse(SmsMessage)` — 100% unit-testable.
- Raw SMS body + hash is always stored (`Sms` table) before any decision. Audit trail first.

## 2. Sender formats

| Region | Format | Example |
|---|---|---|
| India (transactional) | `XY-BANKNME` — 2 alpha series + `-` + 6-char ID, sometimes sent without the prefix | `AD-HDFCBK`, `VM-ICICIB`, `SBIINB` |
| UK | Alphanumeric sender ID | `MONZO`, `BARCLAYS`, `HSBC`, `LLOYDS` |
| US | 5–6 digit short code | `24273`, `692632` |

Normalize: uppercase, strip non-alphanumerics, if it contains `-` keep the part
after it. Store both raw and normalized.

⚠️ On the emulator, `adb emu sms send` injects SMS with a *numeric* sender, so
the whitelist must match by **pattern** (`contains` / regex), never by exact
sender equality. Pattern examples: `HDFC`, `ICICI`, `SBI`, `MONZO`, `AXIS`,
`KOTAK`, `PAYTM`, `AMEX`, `CHASE`.

## 3. Junk filter (checked before extraction — skip immediately)

| Class | Triggers (case-insensitive) | Action |
|---|---|---|
| OTP | `otp`, `one time password`, `verification code`, `do not share` | IGNORED |
| Promo / loan / marketing | `offer`, `pre-approved`, `personal loan`, `emi due`, `kyc pending`, `cashback offer`, `win`, `click here` | IGNORED |
| Balance-only (no txn) | has `balance` but no debit/credit keyword | IGNORED (may update "reported balance" cache) |
| Service (non-money) | `blocked`, `unblocked`, `limit increased`, `statement` | IGNORED |

## 4. Extraction

### Amount
```regex
(?:₹|Rs\.?|INR|£|\$|USD|€|EUR)\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)
```
- Take **all** matches; pick the **first** as transaction amount, unless a
  later match follows a debit/credit keyword — in that case prefer it.
- Reported balance (after `bal`/`avl`) is stored separately, never as the txn amount.
- Edge: some banks use European decimals (`1.234,50`) — detect if pattern `d.ddd,dd` appears.

### Direction
| Direction | Keywords |
|---|---|
| **DEBIT** | debited, spent, paid, purchase, purchase at, withdrawal, ATM, charged, deducted, "you have paid", "transfer to", "card used" |
| **CREDIT** | credited, received, deposit, deposited, refund, salary, cashback, interest credited, "added to your", "you have received", `CR-`, `IMPS CR`, `NEFT CR` |

If both appear → REVIEW queue (ambiguous).

### Account tail
```regex
(?:a/?c(?:count)?|acct|card|xx|x{2,4}|\*{2,6})[\s.:#~-]*((?:XX|x{2}|\*{2,6})?[0-9]{2,6})(?![0-9])
```
Matched against the account's recorded tail (user enters last 3–4 digits when
creating an account). This is what makes the app **account-aware**:
`a/c XX1234` → account "Everyday ••1234" automatically.

### Reference
```regex
(?:ref(?:erence)?|ref no|txn|utr|rrn|cheque|upi[/ ]?ref)[.:\s#]*([A-Za-z0-9]{6,22})
```

### Reported balance (optional, for reconciliation hints)
```regex
(?:avl|available|bal(?:ance)?)\s*[.:\-]*\s*(?:₹|Rs\.?|INR|£|\$)?\s*([0-9,]+(?:\.[0-9]{1,2})?)
```

## 5. Confidence scoring (0–100)

| Signal | Points |
|---|---|
| Sender matches whitelist pattern | +40 |
| Exactly one direction keyword found | +30 |
| Currency-prefixed amount parsed | +20 |
| Account tail parsed **and** mapped to an account | +10 |
| − | −15 if sender not in whitelist |

**≥ 85 → auto-record. 50–84 → review. < 50 → ignored.** Thresholds live in
Settings; default values above.

## 6. Dedup

Primary: `sha256(normalizedSender | body | timestamp)` — unique index on `Sms.hash`.
Secondary guard: `(sender, amountMinor, direction)` within ±60 s → duplicate, skip.

## 7. Test corpus (unit tests — assert exact outcomes)

| # | Sender | Body | Expected |
|---|---|---|---|
| 1 | `AD-HDFCBK` | `Rs.1,234.50 debited from a/c XX1234 on 19-11-26 to UPI/ANIKET/YESB. Avl Bal Rs.45,678.90` | DEBIT ₹1234.50, tail 1234, auto (conf 100) |
| 2 | `VM-ICICIB` | `Rs.25,000.00 credited to a/c XX5678 towards NEFT CR-ACME CORP Ref 045123ABC` | CREDIT ₹25000, tail 5678, auto |
| 3 | `SBIINB` | `Rs.500.00 debited from A/c xx9999 on 19NOV26 14:22 ATM WDL. Bal: Rs.12,345.67` | DEBIT ₹500, tail 9999, auto |
| 4 | `MONZO` | `You spent £12.40 at Tesco. Balance: £1,234.56` | DEBIT £12.40, auto |
| 5 | `BARCLAYS` | `Your account ****4321 was debited £50.00 on 19/11/2026. Available balance £890.12` | DEBIT £50, tail 4321 |
| 6 | `HSBC` | `£1,500.00 credited by SALARY CO on 19 Nov. Balance £2,345.67` | CREDIT £1500 |
| 7 | `24273` | `CHASE: $45.20 debit card purchase at STARBUCKS. Avail bal $1,234.56` | DEBIT $45.20 (short-code sender) |
| 8 | `HDFCBK` | `123456 is your OTP for HDFC netbanking. Do not share.` | IGNORED (OTP) |
| 9 | `AXIBKD` | `Get pre-approved personal loan up to Rs.5,00,000!` | IGNORED (promo) |
| 10 | `SBIBNK` | `Your A/c XX1234 balance is Rs.45,678.90 as on 19-11` | IGNORED (balance-only) |
| 11 | `HDFCBK` | `You have received Rs.299.00 in your a/c XX1234 from RAM via UPI` | CREDIT ₹299 |
| 12 | `PAYTM` | `Paytm: Rs.100 added to your wallet. New balance Rs.340` | CREDIT ₹100 (wallet account) |
| 13 | `BOM-CITIBK` | `Card xx4021 used for $25.99 at AMAZON.COM` | DEBIT $25.99, **currency-mismatch flag** |
| 14 | `HDFCBK` | (exact copy of #1 sent again 3 s later) | duplicate → skipped by dedup |
| 15 | unknown | `Money has been deducted from your account` | IGNORED (<50: no amount, no whitelist) |
| 16 | unknown, mapped manually | `Rs.749.00 spent on card` | REVIEW (no tail, unmapped sender → user assigns) |

## 8. Injecting test SMS into the emulator

```bash
export ANDROID_SDK_ROOT=/opt/homebrew/share/android-commandlinetools
$ANDROID_SDK_ROOT/platform-tools/adb emu sms send HDFCBK "Rs.1,234.50 debited from a/c XX1234 on 19-11-26 to UPI/ANIKET/YESB. Avl Bal Rs.45,678.90"
$ANDROID_SDK_ROOT/platform-tools/adb emu sms send ICICIB "Rs.25,000.00 credited to a/c XX5678 towards NEFT CR-ACME CORP Ref 045123ABC"
$ANDROID_SDK_ROOT/platform-tools/adb emu sms send MONZO "You spent £12.40 at Tesco. Balance: £1,234.56"
$ANDROID_SDK_ROOT/platform-tools/adb emu sms send 24273 "CHASE: \$45.20 debit card purchase at STARBUCKS. Avail bal \$1,234.56"
```

Note: the emulator shows the sender as a phone number/short code; the parser
must not depend on the *sender display format* (see §2 warning).

## 9. v2 (backlog)

- Hindi/Hinglish keyword packs (`rakha gaya`, `prapt hua`) — extension point in keyword tables.
- `NotificationListenerService` for bank-app push notifications (fallback when a
  bank doesn't SMS, common in UK/US) — strictly opt-in, still 100% on-device.
- Auto-pairing transfers: debit SMS from bank A + credit SMS from bank B within
  a 10-minute window and matching amount → suggest "mark as transfer".
