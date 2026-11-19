package app.pocketledger.domain

import app.pocketledger.data.DIR_CREDIT
import app.pocketledger.data.DIR_DEBIT
import app.pocketledger.data.TxnEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class AnalyticsMathTest {

    private val zone: ZoneId = ZoneId.of("UTC")

    private fun at(day: Int, hour: Int): Long =
        LocalDate.of(2026, 11, day).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun txn(
        id: Long,
        day: Int,
        hour: Int,
        direction: String,
        minor: Long,
        merchant: String? = null,
        note: String? = null,
    ) = TxnEntity(
        id = id,
        accountId = 1L,
        direction = direction,
        amountMinor = minor,
        currency = "INR",
        ts = at(day, hour),
        merchant = merchant,
        note = note,
        source = "MANUAL",
        kind = "TXN",
        ref = null,
        transferId = null,
        confidence = null,
        smsId = null,
    )

    @Test
    fun dailySeriesBucketsAndAccumulates() {
        val from = at(1, 0)
        val to = at(4, 0) // exclusive
        val list = listOf(
            txn(1, 1, 10, DIR_DEBIT, 10_000, "Tesco"),
            txn(2, 1, 18, DIR_DEBIT, 5_000, "Tesco"),
            txn(3, 2, 9, DIR_CREDIT, 100_000, "Acme"),
            txn(4, 3, 12, DIR_DEBIT, 20_000, "DMart"),
        )
        val series = AnalyticsMath.dailySeries(list, from, to, openingBalanceMinor = 50_000, zone = zone)
        assertEquals(3, series.size)
        assertEquals(15_000L, series[0].debitMinor)
        assertEquals(35_000L, series[0].balanceMinor) // 50,000 - 15,000
        assertEquals(100_000L, series[1].creditMinor)
        assertEquals(135_000L, series[1].balanceMinor)
        assertEquals(20_000L, series[2].debitMinor)
        assertEquals(115_000L, series[2].balanceMinor)
    }

    @Test
    fun transactionsOutsideRangeAreIgnored() {
        val series = AnalyticsMath.dailySeries(
            listOf(txn(1, 5, 10, DIR_DEBIT, 99_999, "Out")),
            from = at(1, 0), to = at(3, 0), openingBalanceMinor = 1_000, zone = zone,
        )
        assertEquals(2, series.size)
        assertEquals(1_000L, series.last().balanceMinor)
    }

    @Test
    fun topMerchantsAggregatesAndSorts() {
        val list = listOf(
            txn(1, 1, 10, DIR_DEBIT, 30_000, "Tesco"),
            txn(2, 1, 11, DIR_DEBIT, 10_000, "Tesco"),
            txn(3, 1, 12, DIR_DEBIT, 25_000, "DMart"),
            txn(4, 1, 13, DIR_CREDIT, 500_000, "Acme"),   // credits excluded
            txn(5, 1, 14, DIR_DEBIT, 5_000, note = "Chai"),
        )
        val top = AnalyticsMath.topMerchants(list, limit = 2)
        assertEquals(2, top.size)
        assertEquals("Tesco", top[0].name)
        assertEquals(40_000L, top[0].totalMinor)
        assertEquals(2, top[0].count)
        assertEquals("DMart", top[1].name)
    }

    @Test
    fun deltaPercentHandlesMissingBaseline() {
        assertEquals(50, AnalyticsMath.deltaPercent(150, 100))
        assertEquals(-25, AnalyticsMath.deltaPercent(75, 100))
        assertNull(AnalyticsMath.deltaPercent(100, 0))
        assertNull(AnalyticsMath.deltaPercent(0, 0))
    }
}
