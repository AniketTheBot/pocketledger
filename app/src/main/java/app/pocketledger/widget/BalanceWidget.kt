package app.pocketledger.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import app.pocketledger.App
import app.pocketledger.MainActivity
import app.pocketledger.R
import app.pocketledger.money.Money
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

/** Sticker-styled home-screen widget: active account balance + today's spend. */
class BalanceWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as? App ?: return@launch
                val container = app.container
                val settings = container.settings.current()
                val accounts = container.wallet.accounts()
                val active = accounts.firstOrNull { it.id == settings.activeAccountId && !it.archived }
                    ?: accounts.firstOrNull { !it.archived }
                val currency = active?.currency ?: settings.currency
                val balance = active?.let { container.wallet.balance(it.id) } ?: 0L

                val dayStart = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val today = container.wallet.inOutBetween(dayStart, System.currentTimeMillis() + 1, active?.id)

                val tapIntent = PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

                appWidgetIds.forEach { widgetId ->
                    val views = RemoteViews(context.packageName, R.layout.widget_pocketledger)
                    if (active == null) {
                        views.setTextViewText(R.id.widget_balance, "Set up")
                        views.setTextViewText(R.id.widget_sub, "Open PocketLedger to begin")
                        views.setTextViewText(R.id.widget_today, "")
                    } else {
                        views.setTextViewText(R.id.widget_balance, Money.format(balance, currency))
                        views.setTextViewText(R.id.widget_sub, "${active.name} · opening ${Money.format(active.openingBalanceMinor, currency)}")
                        views.setTextViewText(
                            R.id.widget_today,
                            buildString {
                                append("Today: −${Money.format(today.debit, currency)} out")
                                if (today.credit > 0) append(" · +${Money.format(today.credit, currency)} in")
                            },
                        )
                    }
                    views.setOnClickPendingIntent(R.id.widget_root, tapIntent)
                    appWidgetManager.updateAppWidget(widgetId, views)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BalanceWidget::class.java))
            if (ids.isEmpty()) return
            val intent = Intent(context, BalanceWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }
    }
}
