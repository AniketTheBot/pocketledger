package app.pocketledger.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

const val DIR_DEBIT = "DEBIT"
const val DIR_CREDIT = "CREDIT"
const val KIND_TXN = "TXN"
const val KIND_ADJUST = "ADJUST"
const val SRC_MANUAL = "MANUAL"
const val SRC_SMS = "SMS"
const val STATUS_PENDING = "PENDING"
const val STATUS_PROCESSED = "PROCESSED"
const val STATUS_REVIEW = "REVIEW"
const val STATUS_IGNORED = "IGNORED"

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,               // CHECKING | SAVINGS | WALLET | CREDIT | CASH
    val currency: String,           // INR | GBP | USD | EUR
    val openingBalanceMinor: Long,  // exact, from onboarding keypad
    val tail: String?,              // last 3-4 digits for SMS auto-mapping
    val sortOrder: Int = 0,
    val archived: Boolean = false,
    val createdAt: Long,
)

@Entity(
    tableName = "txns",
    indices = [Index("accountId"), Index("ts"), Index("smsId"), Index("transferId")]
)
data class TxnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val direction: String,          // DEBIT | CREDIT
    val amountMinor: Long,
    val currency: String,
    val ts: Long,
    val merchant: String?,
    val note: String?,
    val source: String,             // MANUAL | SMS
    val kind: String = KIND_TXN,    // TXN | ADJUST
    val ref: String?,
    val transferId: String?,        // links transfer pairs
    val confidence: Int?,
    val smsId: Long?,
)

@Entity(
    tableName = "sms",
    indices = [Index(value = ["hash"], unique = true), Index("status")]
)
data class SmsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val senderNorm: String,
    val body: String,
    val ts: Long,
    val hash: String,
    val status: String,             // PENDING | PROCESSED | REVIEW | IGNORED
    val junk: String?,              // parser verdict for ignored
    val direction: String?,
    val amountMinor: Long?,
    val currency: String?,
    val accountTail: String?,
    val ref: String?,
    val merchant: String?,
    val reportedBalanceMinor: Long?,
    val confidence: Int?,
    val accountId: Long?,
    val reason: String?,
)

@Entity(tableName = "sender_rules")
data class SenderRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pattern: String,            // contains-match on normalized sender
    val accountId: Long?,
    val createdAt: Long,
)

data class InOutTotals(val credit: Long, val debit: Long)

@Dao
interface AccountDao {
    @Insert
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Query("SELECT * FROM accounts ORDER BY archived ASC, sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY archived ASC, sortOrder ASC, id ASC")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE archived = 0 ORDER BY sortOrder ASC, id ASC")
    suspend fun active(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun byId(id: Long): AccountEntity?
}

@Dao
interface TxnDao {
    @Insert
    suspend fun insert(txn: TxnEntity): Long

    @Insert
    suspend fun insertAll(txns: List<TxnEntity>)

    @Update
    suspend fun update(txn: TxnEntity)

    @Query("DELETE FROM txns WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM txns WHERE transferId = :transferId")
    suspend fun deleteByTransfer(transferId: String)

    @Query("SELECT * FROM txns WHERE id = :id")
    suspend fun byId(id: Long): TxnEntity?

    @Query("SELECT * FROM txns WHERE transferId = :transferId")
    suspend fun byTransfer(transferId: String): List<TxnEntity>

    @Query("SELECT * FROM txns ORDER BY ts DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TxnEntity>>

    @Query("SELECT * FROM txns ORDER BY ts ASC, id ASC")
    suspend fun all(): List<TxnEntity>

    @Query("SELECT * FROM txns WHERE accountId = :accountId ORDER BY ts DESC, id DESC LIMIT :limit")
    fun observeRecentForAccount(accountId: Long, limit: Int): Flow<List<TxnEntity>>

    @Query(
        "SELECT COALESCE(a.openingBalanceMinor, 0) + COALESCE((" +
            "SELECT SUM(CASE WHEN t.direction = 'CREDIT' THEN t.amountMinor ELSE -t.amountMinor END) " +
            "FROM txns t WHERE t.accountId = :accountId AND t.kind = 'TXN'), 0) " +
            "FROM accounts a WHERE a.id = :accountId"
    )
    suspend fun balanceFor(accountId: Long): Long?

    @Query(
        "SELECT COALESCE((SELECT SUM(openingBalanceMinor) FROM accounts WHERE archived = 0), 0) + " +
            "COALESCE((SELECT SUM(CASE WHEN direction = 'CREDIT' THEN amountMinor ELSE -amountMinor END) " +
            "FROM txns WHERE kind = 'TXN' AND accountId IN (SELECT id FROM accounts WHERE archived = 0)), 0)"
    )
    fun observeTotal(): Flow<Long>

    @Query(
        "SELECT COALESCE(SUM(CASE WHEN direction = 'CREDIT' THEN amountMinor ELSE 0 END), 0) AS credit, " +
            "COALESCE(SUM(CASE WHEN direction = 'DEBIT' THEN amountMinor ELSE 0 END), 0) AS debit " +
            "FROM txns WHERE kind = 'TXN' AND transferId IS NULL AND ts >= :from AND ts < :to " +
            "AND (:accountId IS NULL OR accountId = :accountId)"
    )
    fun observeInOut(from: Long, to: Long, accountId: Long?): Flow<InOutTotals>

    @Query(
        "SELECT COALESCE(SUM(CASE WHEN direction = 'CREDIT' THEN amountMinor ELSE 0 END), 0) AS credit, " +
            "COALESCE(SUM(CASE WHEN direction = 'DEBIT' THEN amountMinor ELSE 0 END), 0) AS debit " +
            "FROM txns WHERE kind = 'TXN' AND transferId IS NULL AND ts >= :from AND ts < :to " +
            "AND (:accountId IS NULL OR accountId = :accountId)"
    )
    suspend fun inOutBetween(from: Long, to: Long, accountId: Long?): InOutTotals
}

@Dao
interface SmsDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(sms: SmsEntity): Long

    @Query("SELECT * FROM sms WHERE id = :id")
    suspend fun byId(id: Long): SmsEntity?

    @Query("SELECT * FROM sms WHERE hash = :hash LIMIT 1")
    suspend fun byHash(hash: String): SmsEntity?

    @Query("SELECT * FROM sms ORDER BY ts DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SmsEntity>>

    @Query("SELECT * FROM sms ORDER BY ts ASC, id ASC")
    suspend fun all(): List<SmsEntity>

    @Query("SELECT * FROM sms WHERE status = 'REVIEW' ORDER BY ts DESC")
    fun observeReview(): Flow<List<SmsEntity>>

    @Query("SELECT COUNT(*) FROM sms WHERE status = 'REVIEW'")
    fun observeReviewCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sms WHERE status = :status")
    suspend fun count(status: String): Int

    @Query(
        "UPDATE sms SET status = :status, confidence = :confidence, accountId = :accountId, reason = :reason " +
            "WHERE id = :id"
    )
    suspend fun setStatus(id: Long, status: String, confidence: Int?, accountId: Long?, reason: String?)

    @Query(
        "UPDATE sms SET status = 'PROCESSED', accountId = :accountId, direction = :direction, " +
            "amountMinor = :amountMinor, merchant = :merchant, reason = 'user_confirmed' WHERE id = :id"
    )
    suspend fun markConfirmed(
        id: Long,
        accountId: Long,
        direction: String,
        amountMinor: Long,
        merchant: String?,
    )

    @Query("UPDATE sms SET status = 'IGNORED', reason = 'user_discarded' WHERE id = :id")
    suspend fun markDiscarded(id: Long)
}

@Dao
interface SenderRuleDao {
    @Insert
    suspend fun insert(rule: SenderRuleEntity): Long

    @Query("DELETE FROM sender_rules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM sender_rules ORDER BY id DESC")
    fun observeAll(): Flow<List<SenderRuleEntity>>

    @Query("SELECT * FROM sender_rules")
    suspend fun all(): List<SenderRuleEntity>
}

@Database(
    entities = [AccountEntity::class, TxnEntity::class, SmsEntity::class, SenderRuleEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun txnDao(): TxnDao
    abstract fun smsDao(): SmsDao
    abstract fun senderRuleDao(): SenderRuleDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "pocketledger.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
