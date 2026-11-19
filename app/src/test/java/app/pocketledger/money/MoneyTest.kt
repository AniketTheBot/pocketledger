package app.pocketledger.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTest {

    @Test
    fun parsesGroupedWesternAmount() {
        assertEquals(2_486_115L, Money.parseMinor("24,861.15")!!)
    }

    @Test
    fun parsesSingleDecimal() {
        assertEquals(2_486_110L, Money.parseMinor("24861.1")!!)
    }

    @Test
    fun parsesEuropeanDecimalAmount() {
        assertEquals(123_450L, Money.parseMinor("1.234,50")!!)
    }

    @Test
    fun parsesIndianGroupingWithSymbol() {
        assertEquals(11_746_115L, Money.parseMinor("₹1,17,461.15")!!)
    }

    @Test
    fun parsesNegativeAmount() {
        assertEquals(-50_000L, Money.parseMinor("-500")!!)
    }

    @Test
    fun rejectsMalformedAmounts() {
        assertNull(Money.parseMinor("1.234.5"))
        assertNull(Money.parseMinor("abc"))
        assertNull(Money.parseMinor("1.2345")) // more than two decimals
    }

    @Test
    fun formatsIndianGrouping() {
        assertEquals("₹1,17,461.15", Money.format(11_746_115L, "INR"))
    }

    @Test
    fun formatsUsThousands() {
        assertEquals("\$1,234.56", Money.format(123_456L, "USD"))
    }

    @Test
    fun negativeFormattingUsesMinusSign() {
        val formatted = Money.format(-123_456L, "USD")
        assertTrue(formatted.startsWith("−"))
        assertEquals("−\$1,234.56", formatted)
    }

    @Test
    fun exportsPlainDecimal() {
        assertEquals("24861.15", Money.toDecimal(2_486_115L))
    }
}
