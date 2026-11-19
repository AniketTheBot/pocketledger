package app.pocketledger.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import app.pocketledger.data.STATUS_IGNORED
import app.pocketledger.data.STATUS_PROCESSED
import app.pocketledger.data.STATUS_REVIEW
import app.pocketledger.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ScanSummary(
    val scanned: Int,
    val processed: Int,
    val review: Int,
    val ignored: Int,
    val duplicates: Int,
) {
    val found: Int get() = processed + review
}

object SmsPermissions {
    fun granted(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
}

/** Reads the device inbox once (or on rescan) and feeds the pipeline. Dedup makes it re-run safe. */
class InboxScanner(
    private val context: Context,
    private val pipeline: SmsPipeline,
    private val settings: SettingsRepository,
) {
    suspend fun scan(
        limit: Int = 5000,
        onProgress: (scanned: Int, total: Int) -> Unit = { _, _ -> },
    ): ScanSummary = withContext(Dispatchers.IO) {
        if (!SmsPermissions.granted(context)) {
            return@withContext ScanSummary(0, 0, 0, 0, 0)
        }
        val uri = Telephony.Sms.Inbox.CONTENT_URI
        val projection = arrayOf("address", "body", "date")
        val cursor = context.contentResolver.query(uri, projection, null, null, "date DESC")
            ?: return@withContext ScanSummary(0, 0, 0, 0, 0)

        var scanned = 0
        var processed = 0
        var review = 0
        var ignored = 0
        var duplicates = 0

        cursor.use { c ->
            val idxAddr = c.getColumnIndexOrThrow("address")
            val idxBody = c.getColumnIndexOrThrow("body")
            val idxDate = c.getColumnIndexOrThrow("date")
            val total = minOf(c.count, limit)
            while (c.moveToNext() && scanned < limit) {
                val sender = c.getString(idxAddr) ?: "UNKNOWN"
                val body = c.getString(idxBody) ?: ""
                val ts = c.getLong(idxDate)
                if (body.isBlank()) continue
                val result = pipeline.ingest(sender, body, ts)
                scanned++
                when (result.status) {
                    STATUS_PROCESSED -> processed++
                    STATUS_REVIEW -> review++
                    STATUS_IGNORED -> ignored++
                    "DUPLICATE" -> duplicates++
                }
                if (scanned % 25 == 0) onProgress(scanned, total)
            }
        }
        settings.setLastScanTs(System.currentTimeMillis())
        onProgress(scanned, scanned)
        ScanSummary(scanned, processed, review, ignored, duplicates)
    }
}
