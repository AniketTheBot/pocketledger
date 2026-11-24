package app.pocketledger.sms

import android.content.Context
import android.provider.Telephony
import app.pocketledger.ml.CorpusExporter
import java.io.File

/**
 * Debug-only: dumps the **entire device SMS inbox** to a JSONL file on disk so a
 * real bank-SMS corpus can be pulled for offline training.
 *
 * Unlike [CorpusExporter] this does not read the app's `sms` table, does not
 * change any balance, and does not require the app to have scanned before. It
 * needs READ_SMS (already declared and used by the scanning feature).
 *
 * Pull with:
 *   adb exec-out run-as app.pocketledger cat files/corpus/inbox-<ts>.jsonl > inbox.jsonl
 */
object InboxExporter {

    data class Result(val file: File?, val rows: Int, val error: String? = null)

    fun export(context: Context, limit: Int = 20_000): Result {
        if (!SmsPermissions.granted(context)) {
            return Result(null, 0, "READ_SMS permission not granted")
        }
        val cursor = runCatching {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf("address", "body", "date"),
                null,
                null,
                "date DESC",
            )
        }.getOrNull() ?: return Result(null, 0, "inbox query failed")

        val dir = File(context.filesDir, "corpus").apply { mkdirs() }
        val file = File(dir, "inbox-${System.currentTimeMillis()}.jsonl")
        var rows = 0

        cursor.use { c ->
            val iAddr = c.getColumnIndexOrThrow("address")
            val iBody = c.getColumnIndexOrThrow("body")
            val iDate = c.getColumnIndexOrThrow("date")
            file.bufferedWriter().use { w ->
                while (c.moveToNext() && rows < limit) {
                    val sender = c.getString(iAddr) ?: "UNKNOWN"
                    val body = c.getString(iBody) ?: ""
                    val ts = c.getLong(iDate)
                    if (body.isBlank()) continue
                    w.write(line(sender, body, ts))
                    w.write("\n")
                    rows++
                }
            }
        }
        return Result(file, rows)
    }

    /**
     * One JSON object per message. Parser fields are included only as hints so
     * the corpus review can see which phrasings already parse; they are never
     * treated as ground truth.
     */
    private fun line(sender: String, body: String, ts: Long): String {
        val parsed = SmsParser.parse(sender, body)
        val sb = StringBuilder(body.length + 320)
        sb.append('{')
        fun put(key: String, value: String?) {
            sb.append('"').append(key).append("\":")
            sb.append(if (value == null) "null" else '"' + CorpusExporter.jsonEscape(value) + '"')
        }
        fun putN(key: String, value: Number?) {
            sb.append('"').append(key).append("\":").append(value?.toString() ?: "null")
        }
        put("sender", sender); sb.append(',')
        put("body", body); sb.append(',')
        putN("ts", ts); sb.append(',')
        put("parsed_junk", parsed.junk); sb.append(',')
        put("parsed_direction", parsed.direction); sb.append(',')
        putN("parsed_amount_minor", parsed.amountMinor); sb.append(',')
        put("parsed_currency", parsed.currency); sb.append(',')
        put("parsed_account_tail", parsed.accountTail)
        sb.append('}')
        return sb.toString()
    }
}
