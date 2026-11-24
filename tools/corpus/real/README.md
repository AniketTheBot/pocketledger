# Real SMS corpus (local only)

Drop real bank messages here. **This folder is git-ignored** — raw messages must
never be committed to the public repo. Only this README and `.gitignore` are
tracked.

## Fastest: export the whole inbox (one command)

No manual copying. With the debug build installed and the phone connected over
USB:

```bash
tools/export_inbox.sh            # auto-picks the USB phone
# or: tools/export_inbox.sh 45291VDJH00989
```

It grants `READ_SMS`, asks the app to dump the **entire inbox** to
`files/corpus/inbox-<ts>.jsonl` (no balances touched), pulls it here, and prints
the message count. The file is git-ignored.

Manual equivalent:

```bash
adb shell pm grant app.pocketledger android.permission.READ_SMS
adb shell am broadcast -n app.pocketledger/.DemoSeedReceiver -a app.pocketledger.DEBUG_EXPORT_INBOX
adb logcat -d -s InboxExport | tail -2          # shows the file name
adb exec-out run-as app.pocketledger cat files/corpus/inbox-<ts>.jsonl > tools/corpus/real/inbox.jsonl
```

## What to include

Aim for **20–50 messages per bank/format**, plus the junk you actually receive:

- debits (card, UPI, ATM, NEFT/IMPS, autopay, subscriptions)
- credits (salary, refunds, incoming UPI, cashback, interest)
- OTPs, promos/loan offers, balance-only alerts, "will be debited" notices
- messages from unknown senders / personal messages

The negative examples matter as much as the transactions — they are what stop
the app recording an OTP as a debit.

## Accepted formats

### `.jsonl` (best)

One JSON object per line:

```json
{"sender":"HDFCBK","body":"Rs.1,234.50 debited from A/c XX1234 on 24-11-26 to VPA shop@upi.","label":"DEBIT","receivedAt":1764000000000}
{"sender":"ICICIB","body":"Your OTP is 482913. Do not share it.","label":"NOT_TXN"}
{"sender":"AXISBK","body":"Rs.500.00 credited to A/c XX9876 by salary.","label":"CREDIT"}
```

- `label` is optional: one of `DEBIT`, `CREDIT`, `NOT_TXN`, `UNKNOWN`.
- Leave it out (or use `UNKNOWN`) if you are not sure — the model work will
  label it and send back a review list.
- Keep the **exact message body**; do not clean up spacing or punctuation.

### `.txt`

One message per file, or blank-line-separated messages in one file. If you have
the sender, start the block with `SENDER: HDFCBK`.

### `.csv`

Columns: `sender,body,label` — header row required.

## Privacy

Real bank SMS contain names, account tails, amounts and sometimes phone
numbers. The folder is ignored so this stays on the dev machine, but if you
want to be extra safe:

- replace names with `XXXXX`
- replace full account/card numbers with the last 4 digits
- keep amounts and bank wording intact — those are what the model needs

## What happens next

The corpus is split **by bank/format**, not by random row, so evaluation checks
generalisation to a bank the model has not memorised. The parser gets golden
tests for every new phrasing; the classifier is retrained and must clear the
existing gates plus a real-message holdout before release.
