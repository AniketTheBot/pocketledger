package app.pocketledger.domain

import androidx.room.withTransaction
import app.pocketledger.data.AppDatabase
import app.pocketledger.data.DIR_CREDIT
import app.pocketledger.data.DIR_DEBIT
import app.pocketledger.data.KIND_ADJUST
import app.pocketledger.data.KIND_TXN
import app.pocketledger.data.SRC_MANUAL
import app.pocketledger.data.TxnEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MoneyOps(private val db: AppDatabase) {

    suspend fun addManual(
        accountId: Long,
        direction: String,
        amountMinor: Long,
        currency: String,
        ts: Long = System.currentTimeMillis(),
        merchant: String? = null,
        note: String? = null,
    ): Long {
        require(amountMinor > 0) { "amount must be positive" }
        require(direction == DIR_DEBIT || direction == DIR_CREDIT)
        return db.txnDao().insert(
            TxnEntity(
                accountId = accountId,
                direction = direction,
                amountMinor = amountMinor,
                currency = currency,
                ts = ts,
                merchant = merchant?.trim()?.ifEmpty { null },
                note = note?.trim()?.ifEmpty { null },
                source = SRC_MANUAL,
                kind = KIND_TXN,
                ref = null,
                transferId = null,
                confidence = null,
                smsId = null,
            )
        )
    }

    /** Two linked rows sharing a transferId. Excluded from in/out summaries. */
    suspend fun transfer(
        fromAccountId: Long,
        toAccountId: Long,
        amountMinor: Long,
        currency: String,
        ts: Long = System.currentTimeMillis(),
        note: String? = null,
    ): String {
        require(fromAccountId != toAccountId) { "transfer needs two different accounts" }
        require(amountMinor > 0) { "amount must be positive" }
        val transferId = UUID.randomUUID().toString()
        db.withTransaction {
            db.txnDao().insertAll(
                listOf(
                    TxnEntity(
                        accountId = fromAccountId, direction = DIR_DEBIT, amountMinor = amountMinor,
                        currency = currency, ts = ts, merchant = null,
                        note = note?.ifEmpty { null } ?: "Transfer out", source = SRC_MANUAL,
                        kind = KIND_TXN, ref = null, transferId = transferId,
                        confidence = null, smsId = null,
                    ),
                    TxnEntity(
                        accountId = toAccountId, direction = DIR_CREDIT, amountMinor = amountMinor,
                        currency = currency, ts = ts, merchant = null,
                        note = note?.ifEmpty { null } ?: "Transfer in", source = SRC_MANUAL,
                        kind = KIND_TXN, ref = null, transferId = transferId,
                        confidence = null, smsId = null,
                    ),
                )
            )
        }
        return transferId
    }

    /**
     * Creates one ADJUST row so the computed balance equals [actualBalanceMinor].
     * Returns null when no adjustment is needed.
     */
    suspend fun reconcile(accountId: Long, actualBalanceMinor: Long): Long? {
        val current = db.txnDao().balanceFor(accountId) ?: 0L
        val diff = actualBalanceMinor - current
        if (diff == 0L) return null
        val direction = if (diff > 0) DIR_CREDIT else DIR_DEBIT
        val day = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
        return db.txnDao().insert(
            TxnEntity(
                accountId = accountId,
                direction = direction,
                amountMinor = kotlin.math.abs(diff),
                currency = db.accountDao().byId(accountId)?.currency ?: "INR",
                ts = System.currentTimeMillis(),
                merchant = null,
                note = "Reconcile $day",
                source = SRC_MANUAL,
                kind = KIND_ADJUST,
                ref = null,
                transferId = null,
                confidence = null,
                smsId = null,
            )
        )
    }

    suspend fun deleteTxn(id: Long) {
        val txn = db.txnDao().byId(id) ?: return
        if (txn.transferId != null) db.txnDao().deleteByTransfer(txn.transferId) else db.txnDao().deleteById(id)
    }
}
