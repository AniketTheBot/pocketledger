package app.pocketledger.ml

import android.content.Context
import app.pocketledger.data.AppDatabase
import app.pocketledger.data.SmsEntity
import java.io.File

/**
 * Dumps the on-device `sms` table as a labeled JSONL corpus for offline
 * retraining (tools/train_classifier.mjs --corpus <file>).
 *
 * Label policy (classes match the trainer: NOT_TXN | DEBIT | CREDIT):
 *  - reason=user_confirmed + direction  -> hard human label
 *  - reason=user_discarded              -> hard human label NOT_TXN
 *  - parser parsed cleanly (junk=null, direction + amount present) -> silver label
 *  - otherwise                          -> null (trainer decides whether to use it)
 *
 * Pull with: adb exec-out run-as app.pocketledger cat files/corpus/<file> > corpus.jsonl
 */
object CorpusExporter {

    data class Result(val file: File, val rows: Int, val hardLabeled: Int, val silverLabeled: Int)

    fun labelFor(e: SmsEntity): Pair<String?, String?> {
        val hard = when {
            e.reason == "user_confirmed" && e.direction != null -> e.direction
            e.reason == "user_discarded" -> "NOT_TXN"
            else -> null
        }
        val silver = if (e.junk == null && e.direction != null && e.amountMinor != null) e.direction else null
        return hard to silver
    }

    suspend fun export(context: Context, db: AppDatabase): Result {
        val rows = db.smsDao().all()
        val dir = File(context.filesDir, "corpus").apply { mkdirs() }
        val file = File(dir, "corpus-${System.currentTimeMillis()}.jsonl")
        var hard = 0
        var silver = 0
        file.bufferedWriter().use { w ->
            for (e in rows) {
                val (label, ideal) = labelFor(e)
                if (label != null) hard++ else if (ideal != null) silver++
                w.write(line(e, label, ideal))
                w.write("\n")
            }
        }
        return Result(file, rows.size, hard, silver)
    }

    private fun line(e: SmsEntity, label: String?, ideal: String?): String {
        val sb = StringBuilder(320)
        sb.append('{')
        fun put(key: String, value: String?) {
            sb.append('"').append(key).append("\":")
            sb.append(if (value == null) "null" else '"' + jsonEscape(value) + '"')
        }
        fun putN(key: String, value: Number?) {
            sb.append('"').append(key).append("\":").append(value?.toString() ?: "null")
        }
        put("sender", e.sender); sb.append(',')
        put("sender_norm", e.senderNorm); sb.append(',')
        put("body", e.body); sb.append(',')
        putN("ts", e.ts); sb.append(',')
        put("status", e.status); sb.append(',')
        put("junk", e.junk); sb.append(',')
        put("direction", e.direction); sb.append(',')
        putN("amount_minor", e.amountMinor); sb.append(',')
        put("currency", e.currency); sb.append(',')
        put("account_tail", e.accountTail); sb.append(',')
        put("ref", e.ref); sb.append(',')
        put("merchant", e.merchant); sb.append(',')
        putN("confidence", e.confidence); sb.append(',')
        put("reason", e.reason); sb.append(',')
        put("label", label); sb.append(',')
        put("ideal_label", ideal)
        sb.append('}')
        return sb.toString()
    }

    fun jsonEscape(s: String): String {
        val sb = StringBuilder(s.length + 8)
        for (ch in s) when (ch) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> if (ch < ' ') sb.append("\\u%04x".format(ch.code)) else sb.append(ch)
        }
        return sb.toString()
    }
}
