package app.pocketledger.sms

import app.pocketledger.data.AppDatabase
import app.pocketledger.data.DIR_CREDIT
import app.pocketledger.data.DIR_DEBIT
import app.pocketledger.data.KIND_TXN
import app.pocketledger.data.SRC_SMS
import app.pocketledger.data.STATUS_IGNORED
import app.pocketledger.data.STATUS_PENDING
import app.pocketledger.data.STATUS_PROCESSED
import app.pocketledger.data.STATUS_REVIEW
import app.pocketledger.data.SettingsRepository
import app.pocketledger.data.SmsEntity
import app.pocketledger.data.TxnEntity
import app.pocketledger.ml.HybridClassifier
import app.pocketledger.ml.SmsClass
import app.pocketledger.ml.SmsClassifier
import java.security.MessageDigest

data class IngestResult(
    val smsId: Long,
    val status: String,
    val confidence: Int,
    val txnId: Long?,
    val reason: String?,
)

/**
 * Deterministic pipeline:
 * dedup → junk veto (rules) → ML classifier → rule extraction → fusion → account mapping → route.
 * The classifier never produces amounts; only [SmsParser] does.
 */
class SmsPipeline(
    private val db: AppDatabase,
    private val classifier: SmsClassifier,
    private val settings: SettingsRepository,
) {

    suspend fun ingest(sender: String, body: String, ts: Long): IngestResult {
        val norm = normalizeSender(sender)
        val hash = sha256("$norm|$body|$ts")
        val parsed = SmsParser.parse(sender, body)
        val ml = runCatching { classifier.classify(body) }.getOrNull()

        val row = SmsEntity(
            sender = sender,
            senderNorm = norm,
            body = body,
            ts = ts,
            hash = hash,
            status = STATUS_PENDING,
            junk = parsed.junk,
            direction = parsed.direction,
            amountMinor = parsed.amountMinor,
            currency = parsed.currency,
            accountTail = parsed.accountTail,
            ref = parsed.ref,
            merchant = parsed.merchant,
            reportedBalanceMinor = parsed.reportedBalanceMinor,
            confidence = null,
            accountId = null,
            reason = null,
        )
        val id = db.smsDao().insertIgnore(row)
        if (id == -1L) return IngestResult(-1, "DUPLICATE", 0, null, "duplicate")

        val cfg = settings.current()

        // The balance anchor is the starting line: anything older is history.
        if (cfg.anchorTs > 0L && ts < cfg.anchorTs) {
            db.smsDao().setStatus(id, STATUS_IGNORED, null, null, "before_anchor")
            return IngestResult(id, STATUS_IGNORED, 0, null, "before_anchor")
        }

        // 1) Deterministic junk veto always wins over the model.
        if (parsed.junk != null) {
            db.smsDao().setStatus(id, STATUS_IGNORED, null, null, parsed.junk.lowercase())
            return IngestResult(id, STATUS_IGNORED, 0, null, parsed.junk)
        }

        // 2) Confidence scoring.
        var conf = 0
        val whitelisted = WHITELIST.any { norm.contains(it) }
        if (whitelisted) conf += 40 else conf -= 15
        if (parsed.direction != null) conf += 30
        if (parsed.amountMinor != null) conf += 20

        val accounts = db.accountDao().active()
        var account = parsed.accountTail?.let { tail ->
            accounts.firstOrNull { it.tail != null && (it.tail == tail || tail.endsWith(it.tail)) }
        }
        if (account != null) conf += 10
        if (account == null) {
            val rule = db.senderRuleDao().all().firstOrNull { norm.contains(it.pattern) }
            if (rule?.accountId != null) account = accounts.firstOrNull { it.id == rule.accountId }
        }

        // 3) ML fusion — conflicts never auto-write.
        var conflict = false
        if (ml != null) {
            if (parsed.isTransaction) {
                when {
                    ml.label == SmsClass.NOT_TXN && ml.pNotTxn > 0.6f -> conflict = true
                    ml.label == SmsClass.DEBIT && parsed.direction == DIR_CREDIT && ml.pDebit > 0.6f -> conflict = true
                    ml.label == SmsClass.CREDIT && parsed.direction == DIR_DEBIT && ml.pCredit > 0.6f -> conflict = true
                    ml.pTxn >= 0.9f -> conf += 5
                }
            } else if (ml.pTxn >= 0.6f && parsed.amountMinor != null) {
                conflict = true
            }
        }
        if (conflict) conf = minOf(conf, 79)
        conf = conf.coerceIn(0, 100)

        val isTxn = parsed.isTransaction && parsed.amountMinor != null && parsed.direction != null

        if (!isTxn) {
            if (conflict && parsed.amountMinor != null) {
                db.smsDao().setStatus(id, STATUS_REVIEW, conf, account?.id, "ml_says_transaction")
                return IngestResult(id, STATUS_REVIEW, conf, null, "ml_says_transaction")
            }
            db.smsDao().setStatus(id, STATUS_IGNORED, conf, account?.id, "not_transaction")
            return IngestResult(id, STATUS_IGNORED, conf, null, "not_transaction")
        }

        if (account == null) {
            db.smsDao().setStatus(id, STATUS_REVIEW, minOf(conf, 79), null, "account_unmapped")
            return IngestResult(id, STATUS_REVIEW, minOf(conf, 79), null, "account_unmapped")
        }
        if (parsed.currency != null && parsed.currency != account.currency) {
            db.smsDao().setStatus(id, STATUS_REVIEW, minOf(conf, 79), account.id, "currency_mismatch")
            return IngestResult(id, STATUS_REVIEW, minOf(conf, 79), null, "currency_mismatch")
        }

        // Cross-sender duplicate guard: bank alert + UPI-app alert for the same
        // payment must never book twice. Anything suspicious goes to Review.
        val duplicate = db.txnDao().similar(
            accountId = account.id,
            direction = parsed.direction!!,
            amountMinor = parsed.amountMinor!!,
            from = ts - 120_000L,
            to = ts + 120_000L,
        )
        if (duplicate != null) {
            db.smsDao().setStatus(id, STATUS_REVIEW, minOf(conf, 79), account.id, "possible_duplicate")
            return IngestResult(id, STATUS_REVIEW, minOf(conf, 79), null, "possible_duplicate")
        }

        // 4) Route.
        if (conf >= cfg.autoThreshold && !conflict) {
            val txnId = db.txnDao().insert(
                TxnEntity(
                    accountId = account.id,
                    direction = parsed.direction!!,
                    amountMinor = parsed.amountMinor!!,
                    currency = parsed.currency ?: account.currency,
                    ts = ts,
                    merchant = parsed.merchant,
                    note = null,
                    source = SRC_SMS,
                    kind = KIND_TXN,
                    ref = parsed.ref,
                    transferId = null,
                    confidence = conf,
                    smsId = id,
                )
            )
            db.smsDao().setStatus(id, STATUS_PROCESSED, conf, account.id, "auto_recorded")
            return IngestResult(id, STATUS_PROCESSED, conf, txnId, "auto_recorded")
        }
        if (conf >= cfg.reviewThreshold) {
            db.smsDao().setStatus(id, STATUS_REVIEW, conf, account.id, "low_confidence")
            return IngestResult(id, STATUS_REVIEW, conf, null, "low_confidence")
        }
        db.smsDao().setStatus(id, STATUS_IGNORED, conf, null, "below_threshold")
        return IngestResult(id, STATUS_IGNORED, conf, null, "below_threshold")
    }

    /**
     * History mode: reads old messages to teach the on-device classifier only.
     * Never books anything, never queues review, never moves a balance.
     * Returns "LEARNED", "IGNORED" or "DUPLICATE".
     */
    suspend fun learnFromHistory(
        sender: String,
        body: String,
        ts: Long,
        allowLearning: Boolean = true,
    ): String {
        val norm = normalizeSender(sender)
        val hash = sha256("$norm|$body|$ts")
        val parsed = SmsParser.parse(sender, body)
        val id = db.smsDao().insertIgnore(
            SmsEntity(
                sender = sender,
                senderNorm = norm,
                body = body,
                ts = ts,
                hash = hash,
                status = STATUS_PENDING,
                junk = parsed.junk,
                direction = parsed.direction,
                amountMinor = parsed.amountMinor,
                currency = parsed.currency,
                accountTail = parsed.accountTail,
                ref = parsed.ref,
                merchant = parsed.merchant,
                reportedBalanceMinor = parsed.reportedBalanceMinor,
                confidence = null,
                accountId = null,
                reason = null,
            )
        )
        if (id == -1L) return "DUPLICATE"

        val ml = runCatching { classifier.classify(body) }.getOrNull()
        val whitelisted = WHITELIST.any { norm.contains(it) }
        val idealLabel = when (parsed.direction) {
            DIR_DEBIT -> SmsClass.DEBIT
            DIR_CREDIT -> SmsClass.CREDIT
            else -> null
        }
        val eligible = allowLearning &&
            parsed.junk == null &&
            parsed.isTransaction &&
            idealLabel != null &&
            parsed.amountMinor != null &&
            (whitelisted || (ml?.pTxn ?: 0f) >= 0.9f) &&
            (ml == null || ml.label == SmsClass.NOT_TXN || ml.label == idealLabel)

        var learned = false
        if (eligible) {
            val hybrid = classifier
            if (hybrid is HybridClassifier) {
                runCatching { hybrid.learn(body, idealLabel!!) }
                learned = true
            }
        }
        db.smsDao().setStatus(
            id,
            STATUS_IGNORED,
            null,
            null,
            if (learned) "history_learning" else "history_skipped",
        )
        return if (learned) "LEARNED" else STATUS_IGNORED
    }

    /** User confirmed a review item (possibly edited in the UI). */
    suspend fun confirm(
        smsId: Long,
        accountId: Long,
        direction: String,
        amountMinor: Long,
        currency: String,
        ts: Long,
        merchant: String?,
        note: String?,
    ): Long {
        val sms = db.smsDao().byId(smsId) ?: error("SMS $smsId not found")
        val before = db.txnDao().balanceFor(accountId) ?: 0L
        val txnId = db.txnDao().insert(
            TxnEntity(
                accountId = accountId,
                direction = direction,
                amountMinor = amountMinor,
                currency = currency,
                ts = ts,
                merchant = merchant?.trim()?.ifEmpty { null },
                note = note?.trim()?.ifEmpty { null },
                source = SRC_SMS,
                kind = KIND_TXN,
                ref = sms.ref,
                transferId = null,
                confidence = 90,
                smsId = smsId,
            )
        )
        db.smsDao().markConfirmed(smsId, accountId, direction, amountMinor, merchant)
        // Historical SMS (received before onboarding completed) are catalogue
        // entries only: neutralise their effect so the anchored balance holds.
        if (sms.ts < settings.current().anchorTs) {
            val after = db.txnDao().balanceFor(accountId) ?: before
            val delta = before - after
            if (delta != 0L) {
                db.accountDao().byId(accountId)?.let { acc ->
                    db.accountDao().update(acc.copy(openingBalanceMinor = acc.openingBalanceMinor + delta))
                }
            }
        }
        learnQuietly(sms.body, if (direction == DIR_DEBIT) SmsClass.DEBIT else SmsClass.CREDIT)
        return txnId
    }

    suspend fun discard(smsId: Long) {
        val sms = db.smsDao().byId(smsId) ?: return
        db.smsDao().markDiscarded(smsId)
        learnQuietly(sms.body, SmsClass.NOT_TXN)
    }

    private suspend fun learnQuietly(body: String, label: SmsClass) {
        val c = classifier
        if (c is HybridClassifier) {
            runCatching { c.learn(body, label) }
        }
    }

    companion object {
        val WHITELIST = listOf(
            "HDFC", "ICICI", "SBI", "AXIS", "KOTAK", "PAYTM", "MONZO", "BARCLAYS", "HSBC",
            "LLOYDS", "NATWEST", "CHASE", "AMEX", "CITI", "BOB", "PNB", "IDFC", "YESB",
            "FED", "RBL", "INDUS", "CANARA", "UNION", "UPI",
        )

        fun normalizeSender(raw: String): String {
            val cleaned = raw.uppercase().replace(Regex("[^A-Z0-9\\-]"), "")
            return if (cleaned.contains("-")) cleaned.substringAfterLast('-') else cleaned
        }

        fun sha256(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
