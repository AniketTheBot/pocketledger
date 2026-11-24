#!/usr/bin/env python3
"""Filter + redact a raw inbox export before it becomes training data.

Default behaviour:
  * keep bank/financial messages only (bank sender id or finance wording)
  * keep bank OTPs as hard negatives, but replace the code with <OTP>
  * redact names, phone numbers, emails, URLs, UPI ids, full card/account numbers
  * keep amounts, verbs and bank phrasing (the model needs those)

Output stays in the git-ignored tools/corpus/real folder. Use --delete-raw to
remove the original inbox dump after a successful clean.

Usage:
  python3 tools/clean_corpus.py                       # newest inbox-*.jsonl
  python3 tools/clean_corpus.py --input inbox.jsonl --delete-raw
"""
from __future__ import annotations

import argparse
import json
import re
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
DEFAULT_DIR = HERE / "corpus" / "real"

BANK_SENDER = re.compile(
    r"(?i)(?:bank|bk|hdfc|icici|sbi|axis|kotak|yesbank|yes|idfc|bob|pnb|canara|"
    r"union|indus|federal|rbl|amex|visa|mastercard|monzo|revolut|chase|barclays|"
    r"hsbc|lloyds|natwest|santander|dbs|citi|standard|woolwich|halifax|nationwide|"
    r"paytm|phonepe|gpay|googlepay|cred|slice|jupiter|fi|au|equitas|bandhan|idbi|"
    r"iob|central|uco|csb|karur|south|city|tide|open|razorpay|mobikwik|freecharge)"
)

FINANCE_BODY = re.compile(
    r"(?i)(?:debited|credited|spent|withdraw|deposit|transfer|balance|txn|"
    r"transaction|upi|neft|imps|rtgs|card|a/c|account|salary|refund|cashback|"
    r"emi|autopay|mandate|payment|paid|received|deducted|charged)"
)

OTP_WORD = re.compile(
    r"(?i)\b(?:otp|one[\s-]+time[\s-]+password|verification[\s-]+code|"
    r"security[\s-]+code|passcode|code)\b"
)
URL = re.compile(r"(?i)\b(?:https?://|www\.)\S+")
EMAIL = re.compile(r"(?i)\b[\w.+-]+@[\w-]+\.[\w.]+\b")
UPI = re.compile(r"(?i)\b[\w.-]{2,}@[a-z]{2,}\b")
# NPCI-style merchant path: UPI/ANIKET/YESB (keep the sentence period)
UPI_SEGMENT = r"[A-Za-z0-9_-]+(?:\.[A-Za-z0-9_-]+)*"
UPI_PATH = re.compile(rf"(?i)\bUPI/{UPI_SEGMENT}(?:/{UPI_SEGMENT})*")
CARD = re.compile(r"\b(?:\d[ -]?){12,19}\b")
LONG_NUM = re.compile(r"\b\d{9,18}\b")
PHONE = re.compile(r"(?<!\d)(?:\+?\d{1,3}[\s-]?)?\d{10}(?!\d)")
NAME = re.compile(
    r"(?i)\b(dear|hi|hello|mr|mrs|ms|miss)\s+"
    r"([A-Z][A-Za-z]+(?:\s+[A-Z][A-Za-z]+){0,2})"
)
# Kept while redacting OTPs so the model still sees the amount and a/c tail.
AMOUNT = re.compile(r"(?i)\b(?:rs\.?|inr|₹)\s*[\d,]+(?:\.\d{1,2})?")
ACCOUNT_TAIL = re.compile(
    r"(?i)\b(?:a/c|ac|account|card(?:\s+ending)?|xx+)\s*[*xX]*\s*(\d{4})\b"
)


def keep_last4(value: str) -> str:
    digits = re.sub(r"\D", "", value)
    return "XXXX" + digits[-4:] if len(digits) >= 4 else "XXXX"


def redact_otp(body: str) -> str:
    """Replace the code in an OTP message, wherever it sits in the sentence.

    "OTP is 123456", "123456 is your OTP" and "code: 8891" all collapse to
    <OTP>. Currency amounts and the a/c or card last-4 are preserved because
    they are the signal the classifier and parser actually need.
    """
    if not OTP_WORD.search(body):
        return body
    kept: list[str] = []

    def stash(match: re.Match[str]) -> str:
        kept.append(match.group(0))
        return f"\x00K{len(kept) - 1}\x00"

    body = AMOUNT.sub(stash, body)
    body = ACCOUNT_TAIL.sub(stash, body)
    body = re.sub(r"\b\d{4,8}\b", "<OTP>", body)
    for i, value in enumerate(kept):
        body = body.replace(f"\x00K{i}\x00", value)
    return body


def redact(body: str) -> str:
    body = redact_otp(body)
    body = URL.sub("<URL>", body)
    body = EMAIL.sub("<EMAIL>", body)
    body = UPI.sub("<UPI>", body)
    body = UPI_PATH.sub("<UPI>", body)
    body = CARD.sub(lambda m: keep_last4(m.group(0)), body)
    body = PHONE.sub("<PHONE>", body)
    body = LONG_NUM.sub(lambda m: keep_last4(m.group(0)), body)
    body = NAME.sub(r"\1 <NAME>", body)
    return body


def relevant(row: dict) -> bool:
    sender = str(row.get("sender") or "")
    body = str(row.get("body") or "")
    if row.get("parsed_direction") or row.get("parsed_amount_minor") is not None:
        return True
    if OTP_WORD.search(body):
        # OTPs are kept as hard negatives, with the code redacted.
        return True
    return bool(BANK_SENDER.search(sender) or FINANCE_BODY.search(body))


def clean_file(path: Path, out_dir: Path) -> tuple[Path, int, int, int]:
    rows = [json.loads(line) for line in path.read_text().splitlines() if line.strip()]
    kept = []
    otps = 0
    for row in rows:
        if not relevant(row):
            continue
        body = str(row.get("body") or "")
        if OTP_WORD.search(body):
            otps += 1
        row = dict(row)
        row["body"] = redact(body)
        row["redacted"] = True
        kept.append(row)

    out_dir.mkdir(parents=True, exist_ok=True)
    out = out_dir / f"clean-{int(time.time() * 1000)}.jsonl"
    with out.open("w") as f:
        for row in kept:
            f.write(json.dumps(row, ensure_ascii=False) + "\n")
    return out, len(rows), len(kept), otps


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--input", type=Path, help="raw inbox-*.jsonl (default: newest in folder)")
    ap.add_argument("--out-dir", type=Path, default=DEFAULT_DIR)
    ap.add_argument("--delete-raw", action="store_true", help="delete the raw dump after cleaning")
    args = ap.parse_args()

    if args.input:
        inputs = [args.input]
    else:
        candidates = sorted(DEFAULT_DIR.glob("inbox-*.jsonl"), key=lambda p: p.stat().st_mtime)
        if not candidates:
            print(f"✗ no inbox-*.jsonl in {DEFAULT_DIR}", file=sys.stderr)
            return 1
        inputs = [candidates[-1]]

    total = kept_total = otps_total = 0
    outputs = []
    for path in inputs:
        if not path.exists():
            print(f"✗ missing {path}", file=sys.stderr)
            return 1
        out, total_n, kept_n, otps = clean_file(path, args.out_dir)
        outputs.append(out)
        total += total_n
        kept_total += kept_n
        otps_total += otps
        if args.delete_raw:
            path.unlink()

    for out in outputs:
        print(f"✓ {out}")
    print(f"  kept {kept_total}/{total} bank/financial messages · {otps_total} OTP examples redacted")
    if args.delete_raw:
        print("  raw inbox dump deleted")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
