package app.pocketledger.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.pocketledger.ml.HybridClassifier
import app.pocketledger.sms.InboxScanner
import app.pocketledger.sms.SmsPipeline
import app.pocketledger.domain.MoneyOps
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "pocketledger_settings")

data class AppSettings(
    val onboarded: Boolean = false,
    val currency: String = "INR",
    val userName: String = "",
    val activeAccountId: Long = -1L,
    val lastScanTs: Long = 0L,
    val anchorTs: Long = 0L,
    val autoThreshold: Int = 85,
    val reviewThreshold: Int = 50,
    val scanningEnabled: Boolean = true,
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val CURRENCY = stringPreferencesKey("currency")
        val USER_NAME = stringPreferencesKey("userName")
        val ACTIVE_ACCOUNT = longPreferencesKey("activeAccountId")
        val LAST_SCAN = longPreferencesKey("lastScanTs")
        val ANCHOR_TS = longPreferencesKey("anchorTs")
        val AUTO_THRESHOLD = intPreferencesKey("autoThreshold")
        val REVIEW_THRESHOLD = intPreferencesKey("reviewThreshold")
        val SCANNING = booleanPreferencesKey("scanningEnabled")
    }

    val settings: Flow<AppSettings> = context.settingsStore.data.map { p ->
        AppSettings(
            onboarded = p[Keys.ONBOARDED] ?: false,
            currency = p[Keys.CURRENCY] ?: "INR",
            userName = p[Keys.USER_NAME] ?: "",
            activeAccountId = p[Keys.ACTIVE_ACCOUNT] ?: -1L,
            lastScanTs = p[Keys.LAST_SCAN] ?: 0L,
            anchorTs = p[Keys.ANCHOR_TS] ?: 0L,
            autoThreshold = p[Keys.AUTO_THRESHOLD] ?: 85,
            reviewThreshold = p[Keys.REVIEW_THRESHOLD] ?: 50,
            scanningEnabled = p[Keys.SCANNING] ?: true,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setOnboarded(value: Boolean) = context.settingsStore.edit { it[Keys.ONBOARDED] = value }
    suspend fun setCurrency(value: String) = context.settingsStore.edit { it[Keys.CURRENCY] = value }
    suspend fun setUserName(value: String) = context.settingsStore.edit { it[Keys.USER_NAME] = value.trim() }
    suspend fun setActiveAccount(id: Long) = context.settingsStore.edit { it[Keys.ACTIVE_ACCOUNT] = id }
    suspend fun setLastScanTs(ts: Long) = context.settingsStore.edit { it[Keys.LAST_SCAN] = ts }
    suspend fun setAnchorTs(ts: Long) = context.settingsStore.edit { it[Keys.ANCHOR_TS] = ts }
    suspend fun setThresholds(auto: Int, review: Int) = context.settingsStore.edit {
        it[Keys.AUTO_THRESHOLD] = auto.coerceIn(60, 100)
        it[Keys.REVIEW_THRESHOLD] = review.coerceIn(10, 84)
    }
    suspend fun setScanning(enabled: Boolean) = context.settingsStore.edit { it[Keys.SCANNING] = enabled }
}

class WalletRepository(val db: AppDatabase) {

    fun observeAccounts(): Flow<List<AccountEntity>> = db.accountDao().observeAll()
    fun observeRecentTxns(limit: Int = 40): Flow<List<TxnEntity>> = db.txnDao().observeRecent(limit)
    fun observeReviewSms(limit: Int = 60): Flow<List<SmsEntity>> = db.smsDao().observeReview(limit)
    fun observeReviewCount(): Flow<Int> = db.smsDao().observeReviewCount()
    fun observeTotal(): Flow<Long> = db.txnDao().observeTotal()
    fun observeSenderRules(): Flow<List<SenderRuleEntity>> = db.senderRuleDao().observeAll()

    suspend fun accounts(): List<AccountEntity> = db.accountDao().getAll()
    suspend fun activeAccounts(): List<AccountEntity> = db.accountDao().active()
    suspend fun allTxns(): List<TxnEntity> = db.txnDao().all()
    suspend fun allSms(): List<SmsEntity> = db.smsDao().all()

    suspend fun analyticsSnapshot(accountId: Long?, from: Long, to: Long): app.pocketledger.domain.AnalyticsSnapshot {
        val txns = db.txnDao().between(from, to, accountId)
        val opening = db.txnDao().openings(accountId)
        val before = db.txnDao().netBefore(from, accountId)
        val periodLength = (to - from).coerceAtLeast(1L)
        val previousDebit = db.txnDao().between(from - periodLength, from, accountId)
            .filter { it.direction == "DEBIT" }.sumOf { it.amountMinor }
        val daily = app.pocketledger.domain.AnalyticsMath.dailySeries(
            txns = txns,
            from = from,
            to = to,
            openingBalanceMinor = opening + before,
        )
        return app.pocketledger.domain.AnalyticsSnapshot(
            from = from,
            to = to,
            debitMinor = txns.filter { it.direction == "DEBIT" }.sumOf { it.amountMinor },
            creditMinor = txns.filter { it.direction == "CREDIT" }.sumOf { it.amountMinor },
            previousDebitMinor = previousDebit,
            daily = daily,
            topMerchants = app.pocketledger.domain.AnalyticsMath.topMerchants(txns),
            balanceStartMinor = opening + before,
            balanceEndMinor = daily.lastOrNull()?.balanceMinor ?: (opening + before),
        )
    }
    suspend fun account(id: Long): AccountEntity? = db.accountDao().byId(id)
    suspend fun txn(id: Long): TxnEntity? = db.txnDao().byId(id)
    suspend fun sms(id: Long): SmsEntity? = db.smsDao().byId(id)

    suspend fun addAccount(
        name: String,
        type: String,
        currency: String,
        openingBalanceMinor: Long,
        tail: String?,
    ): Long = db.accountDao().insert(
        AccountEntity(
            name = name.trim(),
            type = type,
            currency = currency,
            openingBalanceMinor = openingBalanceMinor,
            tail = tail?.filter { it.isDigit() }?.takeLast(4)?.ifEmpty { null },
            sortOrder = (accounts().maxOfOrNull { it.sortOrder } ?: 0) + 1,
            createdAt = System.currentTimeMillis(),
        )
    )

    suspend fun balance(accountId: Long): Long = db.txnDao().balanceFor(accountId) ?: 0L

    suspend fun balances(): Map<Long, Long> =
        accounts().associate { it.id to balance(it.id) }

    suspend fun total(): Long = balances().filterKeys { id -> accounts().firstOrNull { it.id == id }?.archived == false }.values.sum()

    suspend fun inOutBetween(from: Long, to: Long, accountId: Long?): InOutTotals =
        db.txnDao().inOutBetween(from, to, accountId)

    fun observeInOut(from: Long, to: Long, accountId: Long?): Flow<InOutTotals> =
        db.txnDao().observeInOut(from, to, accountId)

    suspend fun markSms(id: Long, status: String, confidence: Int?, accountId: Long?, reason: String?) =
        db.smsDao().setStatus(id, status, confidence, accountId, reason)

    suspend fun setSmsStatusCounts(): Map<String, Int> = mapOf(
        STATUS_PROCESSED to db.smsDao().count(STATUS_PROCESSED),
        STATUS_REVIEW to db.smsDao().count(STATUS_REVIEW),
        STATUS_IGNORED to db.smsDao().count(STATUS_IGNORED),
    )

    suspend fun addRule(pattern: String, accountId: Long?): Long =
        db.senderRuleDao().insert(
            SenderRuleEntity(
                pattern = pattern.trim().uppercase(),
                accountId = accountId,
                createdAt = System.currentTimeMillis(),
            )
        )

    suspend fun deleteRule(id: Long) = db.senderRuleDao().deleteById(id)

    suspend fun wipe(): Unit = db.clearAllTables()
}

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val db: AppDatabase = AppDatabase.build(appContext)
    val settings = SettingsRepository(appContext)
    val wallet = WalletRepository(db)
    val classifier = HybridClassifier(appContext)
    val money = MoneyOps(db)
    val pipeline = SmsPipeline(db, classifier, settings)
    val scanner = InboxScanner(appContext, pipeline, settings)
}
