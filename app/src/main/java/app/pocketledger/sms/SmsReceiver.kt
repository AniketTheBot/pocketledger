package app.pocketledger.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import app.pocketledger.App
import app.pocketledger.widget.BalanceWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        val sender = messages.first().displayOriginatingAddress
            ?: messages.first().originatingAddress
            ?: "UNKNOWN"
        val body = messages.joinToString("") { it.messageBody ?: "" }
        val ts = messages.first().timestampMillis
        if (body.isBlank()) return

        val app = context.applicationContext as? App ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.container.pipeline.ingest(sender, body, ts)
                BalanceWidget.refresh(context)
            } catch (_: Throwable) {
                // Never crash the receiver; the message stays unprocessed and will be
                // picked up by the next inbox scan (dedup keeps it safe).
            } finally {
                pendingResult.finish()
            }
        }
    }
}
