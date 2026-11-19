package app.pocketledger.domain

import app.pocketledger.data.DIR_DEBIT
import app.pocketledger.data.TxnEntity
import java.time.Instant
import java.time.ZoneId

data class DailyPoint(
    val dayStart: Long,
    val debitMinor: Long,
    val creditMinor: Long,
    val balanceMinor: Long,
)

data class MerchantStat(val name: String, val totalMinor: Long, val count: Int)

data class AnalyticsSnapshot(
    val from: Long,
    val to: Long,
    val debitMinor: Long,
    val creditMinor: Long,
    val previousDebitMinor: Long,
    val daily: List<DailyPoint>,
    val topMerchants: List<MerchantStat>,
    val balanceStartMinor: Long,
    val balanceEndMinor: Long,
) {
    val days: Int get() = daily.size.coerceAtLeast(1)
    val averageDailyDebitMinor: Long get() = debitMinor / days
}

/**
 * Pure analytics aggregation. Transfers are already excluded by the DAO
 * (transferId IS NULL) and ADJUST rows never enter (kind = 'TXN').
 */
object AnalyticsMath {

    fun startOfDay(epochMillis: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
            .atStartOfDay(zone).toInstant().toEpochMilli()

    fun dayStarts(from: Long, to: Long, zone: ZoneId): List<Long> {
        val firstDay = Instant.ofEpochMilli(startOfDay(from, zone)).atZone(zone).toLocalDate()
        val lastDay = Instant.ofEpochMilli((to - 1).coerceAtLeast(from)).atZone(zone).toLocalDate()
        val out = mutableListOf<Long>()
        var day = firstDay
        var guard = 0
        while (!day.isAfter(lastDay) && guard < 800) {
            out.add(day.atStartOfDay(zone).toInstant().toEpochMilli())
            day = day.plusDays(1)
            guard++
        }
        return out
    }

    fun dailySeries(
        txns: List<TxnEntity>,
        from: Long,
        to: Long,
        openingBalanceMinor: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<DailyPoint> {
        val days = dayStarts(from, to, zone)
        if (days.isEmpty()) return emptyList()
        val index = days.withIndex().associate { it.value to it.index }
        val debit = LongArray(days.size)
        val credit = LongArray(days.size)
        txns.forEach { txn ->
            val i = index[startOfDay(txn.ts, zone)] ?: return@forEach
            if (txn.direction == DIR_DEBIT) debit[i] += txn.amountMinor else credit[i] += txn.amountMinor
        }
        var balance = openingBalanceMinor
        return days.mapIndexed { i, day ->
            balance += credit[i] - debit[i]
            DailyPoint(day, debit[i], credit[i], balance)
        }
    }

    fun topMerchants(txns: List<TxnEntity>, limit: Int = 5): List<MerchantStat> =
        txns.filter { it.direction == DIR_DEBIT }
            .groupBy { txn ->
                txn.merchant?.trim()?.ifEmpty { null }
                    ?: txn.note?.trim()?.ifEmpty { null }
                    ?: "Untitled"
            }
            .map { (name, list) -> MerchantStat(name, list.sumOf { it.amountMinor }, list.size) }
            .sortedWith(compareByDescending<MerchantStat> { it.totalMinor }.thenByDescending { it.count })
            .take(limit)

    /** Percent change, null when there is no comparable previous period. */
    fun deltaPercent(current: Long, previous: Long): Int? {
        if (previous <= 0L) return null
        return ((current - previous) * 100.0 / previous).toInt()
    }
}
