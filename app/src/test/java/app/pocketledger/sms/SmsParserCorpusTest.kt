package app.pocketledger.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Corpus tests from design/sms-parsing-playbook.md §7 plus adversarial cases.
 *
 * Row 14 is intentionally absent: duplicate suppression is a pipeline concern
 * (hash + sender/amount/direction window), not a parser concern.
 */
class SmsParserCorpusTest {

    private fun parse(body: String, sender: String = "unknown"): ParsedSms =
        SmsParser.parse(sender, body)

    // ------------------------------------------------------------------
    // Playbook §7 corpus
    // ------------------------------------------------------------------

    @Test
    fun row01_hdfcDebitWithReportedBalance() {
        val r = parse(
            "Rs.1,234.50 debited from a/c XX1234 on 19-11-26 to UPI/ANIKET/YESB. Avl Bal Rs.45,678.90",
            "AD-HDFCBK",
        )
        assertTrue(r.isTransaction)
        assertEquals("DEBIT", r.direction)
        assertEquals(123_450L, r.amountMinor)
        assertEquals("INR", r.currency)
        assertEquals("1234", r.accountTail)
        assertEquals(4_567_890L, r.reportedBalanceMinor)
        assertNull(r.junk)
        assertTrue(r.notes.isEmpty())
    }

    @Test
    fun row02_iciciNeftCreditWithReference() {
        val r = parse(
            "Rs.25,000.00 credited to a/c XX5678 towards NEFT CR-ACME CORP Ref 045123ABC",
            "VM-ICICIB",
        )
        assertTrue(r.isTransaction)
        assertEquals("CREDIT", r.direction)
        assertEquals(2_500_000L, r.amountMinor)
        assertEquals("INR", r.currency)
        assertEquals("5678", r.accountTail)
        assertEquals("045123ABC", r.ref)
        assertNull(r.reportedBalanceMinor)
        assertNull(r.junk)
    }

    @Test
    fun row03_sbiAtmWithdrawal() {
        val r = parse(
            "Rs.500.00 debited from A/c xx9999 on 19NOV26 14:22 ATM WDL. Bal: Rs.12,345.67",
            "SBIINB",
        )
        assertTrue(r.isTransaction)
        assertEquals("DEBIT", r.direction)
        assertEquals(50_000L, r.amountMinor)
        assertEquals("INR", r.currency)
        assertEquals("9999", r.accountTail)
        assertEquals(1_234_567L, r.reportedBalanceMinor)
        assertNull(r.junk)
    }

    @Test
    fun row04_monzoGbpSpend() {
        val r = parse(
            "You spent £12.40 at Tesco. Balance: £1,234.56",
            "MONZO",
        )
        assertTrue(r.isTransaction)
        assertEquals("DEBIT", r.direction)
        assertEquals(1_240L, r.amountMinor)
        assertEquals("GBP", r.currency)
        assertEquals("Tesco", r.merchant)
        assertEquals(123_456L, r.reportedBalanceMinor)
        assertNull(r.accountTail)
        assertNull(r.junk)
    }

    @Test
    fun row05_barclaysMaskedAccount() {
        val r = parse(
            "Your account ****4321 was debited £50.00 on 19/11/2026. Available balance £890.12",
            "BARCLAYS",
        )
        assertTrue(r.isTransaction)
        assertEquals("DEBIT", r.direction)
        assertEquals(5_000L, r.amountMinor)
        assertEquals("GBP", r.currency)
        assertEquals("4321", r.accountTail)
        assertEquals(89_012L, r.reportedBalanceMinor)
        assertNull(r.junk)
    }

    @Test
    fun row06_hsbcSalaryCredit() {
        val r = parse(
            "£1,500.00 credited by SALARY CO on 19 Nov. Balance £2,345.67",
            "HSBC",
        )
        assertTrue(r.isTransaction)
        assertEquals("CREDIT", r.direction)
        assertEquals(150_000L, r.amountMinor)
        assertEquals("GBP", r.currency)
        assertEquals(234_567L, r.reportedBalanceMinor)
        assertNull(r.junk)
    }

    @Test
    fun row07_chaseShortcodeUsdPurchase() {
        val r = parse(
            "CHASE: \$45.20 debit card purchase at STARBUCKS. Avail bal \$1,234.56",
            "24273",
        )
        assertTrue(r.isTransaction)
        assertEquals("DEBIT", r.direction)
        assertEquals(4_520L, r.amountMinor)
        assertEquals("USD", r.currency)
        assertEquals("STARBUCKS", r.merchant)
        assertEquals(123_456L, r.reportedBalanceMinor)
        assertNull(r.junk)
    }

    @Test
    fun row08_otpIsJunk() {
        val r = parse(
            "123456 is your OTP for HDFC netbanking. Do not share.",
            "HDFCBK",
        )
        assertFalse(r.isTransaction)
        assertEquals("OTP", r.junk)
        assertNull(r.direction)
        assertNull(r.amountMinor)
        assertNull(r.accountTail)
        assertTrue(r.notes.isEmpty())
    }

    @Test
    fun row09_promoIsJunk() {
        val r = parse(
            "Get pre-approved personal loan up to Rs.5,00,000!",
            "AXIBKD",
        )
        assertFalse(r.isTransaction)
        assertEquals("PROMO", r.junk)
        assertNull(r.direction)
        // Cheap extraction is still reported, but the junk veto wins.
        assertEquals(50_000_000L, r.amountMinor)
        assertEquals("INR", r.currency)
        assertNull(r.accountTail)
    }

    @Test
    fun row10_balanceOnlyIsJunk() {
        val r = parse(
            "Your A/c XX1234 balance is Rs.45,678.90 as on 19-11",
            "SBIBNK",
        )
        assertFalse(r.isTransaction)
        assertEquals("BALANCE_ONLY", r.junk)
        assertNull(r.direction)
        assertNull(r.amountMinor)
        assertEquals("INR", r.currency)
        assertEquals("1234", r.accountTail)
        assertEquals(4_567_890L, r.reportedBalanceMinor)
    }

    @Test
    fun row11_upiReceiveCredit() {
        val r = parse(
            "You have received Rs.299.00 in your a/c XX1234 from RAM via UPI",
            "HDFCBK",
        )
        assertTrue(r.isTransaction)
        assertEquals("CREDIT", r.direction)
        assertEquals(29_900L, r.amountMinor)
        assertEquals("INR", r.currency)
        assertEquals("1234", r.accountTail)
        assertEquals("RAM", r.merchant)
        assertNull(r.junk)
    }

    @Test
    fun row12_paytmWalletCredit() {
        val r = parse(
            "Paytm: Rs.100 added to your wallet. New balance Rs.340",
            "PAYTM",
        )
        assertTrue(r.isTransaction)
        assertEquals("CREDIT", r.direction)
        assertEquals(10_000L, r.amountMinor)
        assertEquals("INR", r.currency)
        assertEquals(34_000L, r.reportedBalanceMinor)
        assertNull(r.accountTail)
        assertNull(r.junk)
    }

    @Test
    fun row13_citibankUsdCardPurchase() {
        val r = parse(
            "Card xx4021 used for \$25.99 at AMAZON.COM",
            "BOM-CITIBK",
        )
        assertTrue(r.isTransaction)
        assertEquals("DEBIT", r.direction)
        assertEquals(2_599L, r.amountMinor)
        assertEquals("USD", r.currency)
        assertEquals("4021", r.accountTail)
        assertTrue(r.merchant == "AMAZON.COM" || r.merchant == "AMAZON")
        assertNull(r.junk)
    }

    @Test
    fun row15_noAmountCannotBeTransaction() {
        val r = parse(
            "Money has been deducted from your account",
            "unknown",
        )
        assertFalse(r.isTransaction)
        assertNull(r.amountMinor)
        assertNull(r.junk)
        assertEquals("DEBIT", r.direction)
        assertTrue(r.notes.contains("no_amount"))
    }

    @Test
    fun row16_spentWithoutTail() {
        val r = parse(
            "Rs.749.00 spent on card",
            "unknown",
        )
        assertTrue(r.isTransaction)
        assertEquals("DEBIT", r.direction)
        assertEquals(74_900L, r.amountMinor)
        assertEquals("INR", r.currency)
        assertNull(r.accountTail)
        assertNull(r.junk)
    }

    // ------------------------------------------------------------------
    // Adversarial cases
    // ------------------------------------------------------------------

    @Test
    fun europeanDecimalFormatIsSupported() {
        val r = parse("You spent €1.234,50 at ALDI. Balance €2.000,00")
        assertTrue(r.isTransaction)
        assertEquals("DEBIT", r.direction)
        assertEquals(123_450L, r.amountMinor)
        assertEquals("EUR", r.currency)
        assertEquals("ALDI", r.merchant)
        assertEquals(200_000L, r.reportedBalanceMinor)
    }

    @Test
    fun negativeAmountFlipsDirection() {
        val r = parse("−₹500.00 debited from a/c XX1234")
        assertTrue(r.isTransaction)
        assertEquals("CREDIT", r.direction)
        assertEquals(50_000L, r.amountMinor)
        assertEquals("INR", r.currency)
        assertEquals("1234", r.accountTail)
        assertNull(r.junk)
    }

    @Test
    fun bothDirectionsProduceAmbiguousNote() {
        val r = parse(
            "Rs.500.00 debited from a/c XX1234 and Rs.200.00 credited to a/c XX5678"
        )
        assertTrue(r.isTransaction)
        assertNull(r.direction)
        assertEquals(50_000L, r.amountMinor)
        assertTrue(r.notes.contains("ambiguous_direction"))
        assertNull(r.junk)
    }

    @Test
    fun reversedDebitBecomesCredit() {
        val r = parse("Rs.100.00 debited from a/c XX1234. Transaction reversed.")
        assertTrue(r.isTransaction)
        assertEquals("CREDIT", r.direction)
        assertEquals(10_000L, r.amountMinor)
        assertEquals("1234", r.accountTail)
    }

    @Test
    fun creditedThenReversedStaysCredit() {
        val r = parse("Rs.200.00 credited to a/c XX1234 and then reversed.")
        assertTrue(r.isTransaction)
        assertEquals("CREDIT", r.direction)
        assertEquals(20_000L, r.amountMinor)
    }

    @Test
    fun balanceBeforeTransactionAmountIsNotTheTransaction() {
        val r = parse("Avl bal Rs.45,678.90. Rs.1,234.50 debited from a/c XX1234")
        assertTrue(r.isTransaction)
        assertEquals("DEBIT", r.direction)
        assertEquals(123_450L, r.amountMinor)
        assertEquals(4_567_890L, r.reportedBalanceMinor)
        assertEquals("1234", r.accountTail)
    }

    @Test
    fun multipleAmountsPreferTheOneNextToDirection() {
        val r = parse(
            "Rs.999.00 debited from a/c XX1234. Service fee Rs.50.00. Avl bal Rs.10,000.00"
        )
        assertTrue(r.isTransaction)
        assertEquals("DEBIT", r.direction)
        assertEquals(99_900L, r.amountMinor)
        assertEquals(1_000_000L, r.reportedBalanceMinor)
        assertEquals("1234", r.accountTail)
    }

    @Test
    fun forwardedPrefixIsIgnored() {
        val r = parse("Fwd: Rs.2,500.00 debited from a/c XX9876. Bal Rs.5,000.00")
        assertTrue(r.isTransaction)
        assertEquals("DEBIT", r.direction)
        assertEquals(250_000L, r.amountMinor)
        assertEquals("9876", r.accountTail)
        assertEquals(500_000L, r.reportedBalanceMinor)
    }

    @Test
    fun atmWithoutAccountTail() {
        val r = parse("Rs.2,000.00 withdrawn at ATM. Avl Bal Rs.10,000.00")
        assertTrue(r.isTransaction)
        assertEquals("DEBIT", r.direction)
        assertEquals(200_000L, r.amountMinor)
        assertNull(r.accountTail)
        assertEquals(1_000_000L, r.reportedBalanceMinor)
        assertNull(r.merchant)
    }

    @Test
    fun salaryCredit() {
        val r = parse("Your salary of Rs.75,000.00 has been credited to a/c XX8888")
        assertTrue(r.isTransaction)
        assertEquals("CREDIT", r.direction)
        assertEquals(7_500_000L, r.amountMinor)
        assertEquals("INR", r.currency)
        assertEquals("8888", r.accountTail)
    }

    @Test
    fun promoWithRealDebitWordStillLosesToJunk() {
        val r = parse(
            "Offer: get Rs.100 cashback when you are debited Rs.1,000. Click here."
        )
        assertFalse(r.isTransaction)
        assertEquals("PROMO", r.junk)
        assertNull(r.direction)
    }

    @Test
    fun upiPaidToPhrasing() {
        val r = parse("You have paid Rs.150.00 to UPI/RAMESH/OKAXIS via UPI")
        assertTrue(r.isTransaction)
        assertEquals("DEBIT", r.direction)
        assertEquals(15_000L, r.amountMinor)
        assertEquals("INR", r.currency)
        assertEquals("UPI/RAMESH", r.merchant)
        assertNull(r.accountTail)
    }

    @Test
    fun youveSpentContraction() {
        val r = parse("You've spent ₹450.00 at BigBasket")
        assertTrue(r.isTransaction)
        assertEquals("DEBIT", r.direction)
        assertEquals(45_000L, r.amountMinor)
        assertEquals("INR", r.currency)
        assertEquals("BigBasket", r.merchant)
    }

    @Test
    fun creditCardTypeDoesNotMakeDirectionAmbiguous() {
        val r = parse("Credit Card xx4021 spent Rs.250.00 at BIGBAZAAR")
        assertTrue(r.isTransaction)
        assertEquals("DEBIT", r.direction)
        assertEquals(25_000L, r.amountMinor)
        assertEquals("4021", r.accountTail)
        assertEquals("BIGBAZAAR", r.merchant)
        assertFalse(r.notes.contains("ambiguous_direction"))
    }

    @Test
    fun amountWithoutDirectionIsUnparseable() {
        val r = parse("Your monthly fee is Rs.99.00")
        assertFalse(r.isTransaction)
        assertEquals("UNPARSEABLE", r.junk)
        assertNull(r.direction)
        assertEquals(9_900L, r.amountMinor)
        assertEquals("INR", r.currency)
        assertTrue(r.notes.contains("direction_missing"))
    }

    @Test
    fun serviceMessageIsJunk() {
        val r = parse("Your card has been blocked. Please call us.", "HDFCBK")
        assertFalse(r.isTransaction)
        assertEquals("SERVICE", r.junk)
        assertNull(r.direction)
        assertNull(r.amountMinor)
    }

    @Test
    fun atmCardBalanceIsBalanceOnlyNotTransaction() {
        val r = parse("Your ATM card balance is Rs.500.00")
        assertFalse(r.isTransaction)
        assertEquals("BALANCE_ONLY", r.junk)
        assertNull(r.amountMinor)
        assertEquals(50_000L, r.reportedBalanceMinor)
    }

    @Test
    fun accountEndingPhrasingIsRecognised() {
        val r = parse("Your account ending 4321 was debited £50.00")
        assertTrue(r.isTransaction)
        assertEquals("DEBIT", r.direction)
        assertEquals("4321", r.accountTail)
    }

    @Test
    fun chequeNumberReferenceIsRecognised() {
        val r = parse("Rs.500.00 credited by cheque no 123456 to a/c XX1234")
        assertTrue(r.isTransaction)
        assertEquals("CREDIT", r.direction)
        assertEquals(50_000L, r.amountMinor)
        assertEquals("123456", r.ref)
        assertEquals("1234", r.accountTail)
        assertNull(r.merchant)
    }

    @Test
    fun balanceLabelWithDateStillLabelsTheBalance() {
        val r = parse(
            "Balance as on 19 Nov 2026 is Rs.1,000.00. Rs.500.00 debited from a/c XX1234"
        )
        assertTrue(r.isTransaction)
        assertEquals("DEBIT", r.direction)
        assertEquals(50_000L, r.amountMinor)
        assertEquals(100_000L, r.reportedBalanceMinor)
    }

    @Test
    fun balanceOnlyPrefersLabelledBalanceOverLaterAmount() {
        val r = parse("Avl bal Rs.1,000.00. Monthly fee Rs.99.00")
        assertFalse(r.isTransaction)
        assertEquals("BALANCE_ONLY", r.junk)
        assertNull(r.amountMinor)
        assertEquals(100_000L, r.reportedBalanceMinor)
    }

    @Test
    fun balanceMentionedAfterAmountIsStillReported() {
        val r = parse("Rs.45,678.90 is your available balance")
        assertFalse(r.isTransaction)
        assertEquals("BALANCE_ONLY", r.junk)
        assertNull(r.amountMinor)
        assertEquals(4_567_890L, r.reportedBalanceMinor)
    }
}
