"""Privacy regression tests for tools/clean_corpus.py.

Run with:
  python3 tools/test_clean_corpus.py
"""
from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from clean_corpus import clean_file, redact, relevant


class RedactTest(unittest.TestCase):
    def test_otp_code_after_word(self) -> None:
        out = redact("Your OTP is 8891 for card ending 4021. Never share it.")
        self.assertNotIn("8891", out)
        self.assertIn("<OTP>", out)
        self.assertIn("4021", out)  # card tail is a useful parser feature

    def test_otp_code_before_word(self) -> None:
        out = redact("123456 is your OTP for txn of Rs.499 at Amazon.")
        self.assertNotIn("123456", out)
        self.assertIn("<OTP>", out)
        self.assertIn("Rs.499", out)  # amount must survive

    def test_phone_upi_and_name(self) -> None:
        out = redact(
            "Dear ANIKET, Rs.25,000 credited to a/c XX5678 by NEFT. "
            "Call 9876543210 or pay to UPI/ANIKET/YESB."
        )
        self.assertNotIn("ANIKET", out)
        self.assertNotIn("9876543210", out)
        self.assertIn("<NAME>", out)
        self.assertIn("<PHONE>", out)
        self.assertIn("<UPI>", out)
        self.assertIn("XX5678", out)
        self.assertIn("Rs.25,000", out)

    def test_plain_message_untouched(self) -> None:
        body = "Rs.749.00 spent on your card ending 4021"
        self.assertEqual(redact(body), body)


class RelevanceTest(unittest.TestCase):
    def test_bank_senders_and_finance_wording(self) -> None:
        self.assertTrue(relevant({"sender": "HDFCBK", "body": "hello"}))
        self.assertTrue(relevant({"sender": "AMZNIN", "body": "Rs.499 debited"}))
        self.assertTrue(
            relevant(
                {
                    "sender": "RANDOM",
                    "body": "Your OTP is 1234",
                    "parsed_direction": None,
                }
            )
        )

    def test_personal_and_promo_dropped(self) -> None:
        self.assertFalse(
            relevant({"sender": "AMZNIN", "body": "Your order has shipped."})
        )
        self.assertFalse(
            relevant({"sender": "+919876543210", "body": "Dinner tonight?"})
        )


class CleanFileTest(unittest.TestCase):
    def test_only_relevant_rows_survive_with_redaction(self) -> None:
        rows = [
            {
                "sender": "HDFCBK",
                "body": "Rs.1,234.50 debited from a/c XX1234 to UPI/ANIKET/YESB.",
                "ts": 1,
                "parsed_direction": "DEBIT",
                "parsed_amount_minor": 123450,
            },
            {
                "sender": "HDFCBK",
                "body": "123456 is your OTP for txn of Rs.499.",
                "ts": 2,
                "parsed_direction": None,
                "parsed_amount_minor": None,
            },
            {
                "sender": "AMZNIN",
                "body": "Your order has shipped.",
                "ts": 3,
                "parsed_direction": None,
                "parsed_amount_minor": None,
            },
        ]
        with tempfile.TemporaryDirectory() as tmp:
            src = Path(tmp) / "inbox-test.jsonl"
            src.write_text("\n".join(json.dumps(r) for r in rows) + "\n")
            out, total, kept, otps = clean_file(src, Path(tmp))
            self.assertEqual((total, kept, otps), (3, 2, 1))
            cleaned = [json.loads(line) for line in out.read_text().splitlines()]
            self.assertEqual(len(cleaned), 2)
            self.assertTrue(all(row["redacted"] for row in cleaned))
            bodies = " ".join(row["body"] for row in cleaned)
            self.assertNotIn("ANIKET", bodies)
            self.assertNotIn("123456", bodies)
            self.assertIn("<OTP>", bodies)
            self.assertIn("<UPI>", bodies)


if __name__ == "__main__":
    unittest.main(verbosity=2)
