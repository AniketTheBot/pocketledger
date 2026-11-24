package app.pocketledger

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.pocketledger.ml.CorpusExporter
import app.pocketledger.sms.InboxExporter
import app.pocketledger.widget.BalanceWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Debug-only helper so emulator E2E runs don't depend on tapping through onboarding.
 *   adb shell am broadcast -a app.pocketledger.DEBUG_SEED
 *   adb shell am broadcast -a app.pocketledger.DEBUG_SCAN
 * No-op in release builds.
 */
class DemoSeedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!BuildConfig.DEBUG) return
        val app = context.applicationContext as? App ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_SEED -> {
                        val wallet = app.container.wallet
                        if (wallet.accounts().isEmpty()) {
                            wallet.addAccount("Everyday", "CHECKING", "INR", 2_486_115L, "1234")
                            wallet.addAccount("Savings", "SAVINGS", "INR", 9_120_000L, "7788")
                            wallet.addAccount("Cash wallet", "CASH", "INR", 140_000L, null)
                        }
                        val first = wallet.accounts().first()
                        app.container.settings.setCurrency("INR")
                        app.container.settings.setActiveAccount(first.id)
                        app.container.settings.setOnboarded(true)
                    }
                    ACTION_SCAN -> app.container.scanner.scan()
                    ACTION_EXPORT -> {
                        val result = CorpusExporter.export(app, app.container.db)
                        Log.i(
                            "CorpusExport",
                            "wrote ${result.rows} rows " +
                                "(${result.hardLabeled} human-labeled, ${result.silverLabeled} silver) " +
                                "-> files/corpus/${result.file.name}"
                        )
                    }
                    ACTION_EXPORT_INBOX -> {
                        val result = InboxExporter.export(app)
                        if (result.error != null) {
                            Log.w("InboxExport", "failed: ${result.error}")
                        } else {
                            Log.i(
                                "InboxExport",
                                "wrote ${result.rows} inbox rows -> files/corpus/${result.file!!.name}"
                            )
                        }
                    }
                }
                BalanceWidget.refresh(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_SEED = "app.pocketledger.DEBUG_SEED"
        const val ACTION_SCAN = "app.pocketledger.DEBUG_SCAN"
        const val ACTION_EXPORT = "app.pocketledger.DEBUG_EXPORT_CORPUS"
        const val ACTION_EXPORT_INBOX = "app.pocketledger.DEBUG_EXPORT_INBOX"
    }
}
