package app.pocketledger.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.pocketledger.data.AppContainer
import app.pocketledger.domain.AnalyticsMath
import app.pocketledger.domain.AnalyticsSnapshot
import app.pocketledger.domain.DailyPoint
import app.pocketledger.money.Money
import app.pocketledger.ui.components.CandyChip
import app.pocketledger.ui.components.FilterChipSticker
import app.pocketledger.ui.components.SectionLabel
import app.pocketledger.ui.components.StickerCard
import app.pocketledger.ui.theme.CardCream
import app.pocketledger.ui.theme.Cream
import app.pocketledger.ui.theme.Green
import app.pocketledger.ui.theme.Ink
import app.pocketledger.ui.theme.Mustard
import app.pocketledger.ui.theme.MutedInk
import app.pocketledger.ui.theme.Pink
import app.pocketledger.ui.theme.Purple
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import kotlin.math.max

private data class PeriodOption(val key: String, val label: String)
private val Periods = listOf(
    PeriodOption("month", "This month"),
    PeriodOption("30d", "Last 30 days"),
    PeriodOption("90d", "Last 3 months"),
)

@Composable
fun AnalyzerDialog(container: AppContainer, onDismiss: () -> Unit) {
    val accounts by container.wallet.observeAccounts().collectAsState(initial = emptyList())
    val settings by container.settings.settings.collectAsState(initial = null)
    val scope = rememberCoroutineScopeSafe()

    var period by remember { mutableStateOf("30d") }
    var accountId by remember {
        mutableStateOf<Long?>(settings?.activeAccountId?.takeIf { id -> accounts.any { it.id == id } })
    }
    var snapshot by remember { mutableStateOf<AnalyticsSnapshot?>(null) }
    var loading by remember { mutableStateOf(true) }

    val zone = remember { ZoneId.systemDefault() }
    val range = remember(period) {
        val now = System.currentTimeMillis()
        when (period) {
            "month" -> LocalDate.now(zone).withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli() to now + 1
            "30d" -> now - 30L * 86_400_000L to now + 1
            else -> now - 90L * 86_400_000L to now + 1
        }
    }
    val currency = accounts.firstOrNull { it.id == accountId }?.currency ?: "INR"
    val accountLabel = accounts.firstOrNull { it.id == accountId }?.name ?: "All accounts"

    LaunchedEffect(range, accountId) {
        loading = true
        snapshot = container.wallet.analyticsSnapshot(accountId, range.first, range.second)
        loading = false
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = Cream) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Smart analyzer", style = MaterialTheme.typography.headlineMedium, color = Ink)
                        Text("Computed on this phone · $accountLabel", fontSize = 10.5.sp, color = MutedInk)
                    }
                    Text(
                        "Close",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MutedInk,
                        modifier = Modifier.clickable { onDismiss() },
                    )
                }

                Spacer(Modifier.height(10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Periods.forEach { p ->
                        FilterChipSticker(p.label, period == p.key, { period = p.key })
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChipSticker("All accounts", accountId == null, { accountId = null })
                    accounts.filter { !it.archived }.forEach { acc ->
                        FilterChipSticker(acc.name, accountId == acc.id, { accountId = acc.id })
                    }
                }

                Spacer(Modifier.height(10.dp))
                val snap = snapshot
                if (loading || snap == null) {
                    Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                        Text("Crunching your numbers…", fontSize = 12.sp, color = MutedInk)
                    }
                } else {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        OutHeroCard(snap, currency)
                        Spacer(Modifier.height(10.dp))
                        PulseCard(snap, currency)
                        Spacer(Modifier.height(10.dp))
                        BalanceTrailCard(snap, currency)
                        Spacer(Modifier.height(10.dp))
                        TopMerchantsCard(snap, currency)
                        Spacer(Modifier.height(10.dp))
                        HowToCard()
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun OutHeroCard(snap: AnalyticsSnapshot, currency: String) {
    val delta = AnalyticsMath.deltaPercent(snap.debitMinor, snap.previousDebitMinor)
    StickerCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("MONEY OUT", fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp, color = MutedInk)
            delta?.let {
                CandyChip(
                    (if (it >= 0) "↑ " else "↓ ") + kotlin.math.abs(it) + "% vs previous",
                    bg = if (it >= 0) Pink else Green,
                    pill = true,
                )
            } ?: CandyChip("no previous period", bg = CardCream, pill = true)
        }
        Spacer(Modifier.height(2.dp))
        Text(Money.format(snap.debitMinor, currency), style = MaterialTheme.typography.displayLarge, color = Ink)
        Text(
            "≈ ${Money.format(snap.averageDailyDebitMinor, currency)} / day · in ${Money.format(snap.creditMinor, currency)} this period",
            fontSize = 10.5.sp,
            color = MutedInk,
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            InOutDonut(credit = snap.creditMinor, debit = snap.debitMinor, Modifier.width(64.dp).height(64.dp))
            Column {
                LegendDot(Green, "in", Money.format(snap.creditMinor, currency))
                Spacer(Modifier.height(3.dp))
                LegendDot(Pink, "out", Money.format(snap.debitMinor, currency))
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(9.dp).height(9.dp).background(color, RoundedCornerShape(percent = 50)))
        Spacer(Modifier.width(6.dp))
        Text("$label  ", fontSize = 10.5.sp, color = MutedInk)
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
    }
}

@Composable
private fun InOutDonut(credit: Long, debit: Long, modifier: Modifier = Modifier) {
    val total = credit + debit
    Canvas(modifier) {
        val stroke = 12.dp.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(
            color = Color(0xFFEFE9DA),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
        if (total > 0L) {
            var start = -90f
            val creditSweep = credit.toFloat() / total * 360f
            drawArc(
                color = Green,
                startAngle = start,
                sweepAngle = creditSweep,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
                start += creditSweep
            drawArc(
                color = Pink,
                startAngle = start,
                sweepAngle = (360f - creditSweep - 2f).coerceAtLeast(0f),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun PulseCard(snap: AnalyticsSnapshot, currency: String) {
    val daily = snap.daily
    val maxDebit = daily.maxOfOrNull { it.debitMinor } ?: 0L
    val maxDay = daily.maxByOrNull { it.debitMinor }
    val fmt = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }
    StickerCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("SPENDING PULSE", fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp, color = MutedInk)
            maxDay?.takeIf { it.debitMinor > 0 }?.let {
                Text("top ${Money.format(it.debitMinor, currency)}", fontSize = 9.5.sp, color = MutedInk)
            }
        }
        Spacer(Modifier.height(8.dp))
        Canvas(Modifier.fillMaxWidth().height(96.dp)) {
            if (daily.isEmpty()) return@Canvas
            val gap = 3.dp.toPx()
            val barWidth = ((size.width - gap * (daily.size - 1)) / daily.size).coerceAtLeast(2f)
            val maxBarHeight = size.height - 4.dp.toPx()
            val baseline = size.height
            daily.forEachIndexed { i, day ->
                val fraction = if (maxDebit == 0L) 0f else day.debitMinor.toFloat() / maxDebit
                val h = (fraction * maxBarHeight).coerceAtLeast(if (day.debitMinor > 0) 4.dp.toPx() else 2.dp.toPx())
                val x = i * (barWidth + gap)
                drawRoundRect(
                    color = if (day.debitMinor == maxDebit && maxDebit > 0) Mustard else Pink,
                    topLeft = Offset(x, baseline - h),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(barWidth / 2.2f, barWidth / 2.2f),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(daily.firstOrNull()?.let { fmt.format(Date(it.dayStart)) } ?: "", fontSize = 9.sp, color = MutedInk)
            Text(daily.lastOrNull()?.let { fmt.format(Date(it.dayStart)) } ?: "", fontSize = 9.sp, color = MutedInk)
        }
    }
}

@Composable
private fun BalanceTrailCard(snap: AnalyticsSnapshot, currency: String) {
    val points = snap.daily.map { it.balanceMinor }
    val min = points.minOrNull() ?: 0L
    val max = points.maxOrNull() ?: 0L
    StickerCard(Modifier.fillMaxWidth()) {
        Text("BALANCE TRAIL", fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp, color = MutedInk)
        Spacer(Modifier.height(2.dp))
        Text(Money.format(snap.balanceEndMinor, currency), style = MaterialTheme.typography.headlineMedium, color = Ink)
        Text(
            "start ${Money.format(snap.balanceStartMinor, currency)} · low ${Money.format(min, currency)} · high ${Money.format(max, currency)}",
            fontSize = 9.5.sp, color = MutedInk,
        )
        Spacer(Modifier.height(8.dp))
        Canvas(Modifier.fillMaxWidth().height(84.dp)) {
            if (points.size < 2) return@Canvas
            val span = (max - min).coerceAtLeast(1L)
            val stepX = size.width / (points.size - 1)
            val pad = 4.dp.toPx()
            val usableH = size.height - pad * 2
            val path = Path()
            points.forEachIndexed { i, v ->
                val x = i * stepX
                val y = pad + (1f - (v - min).toFloat() / span) * usableH
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = Ink, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
            val lastY = pad + (1f - (points.last() - min).toFloat() / span) * usableH
            drawCircle(color = Mustard, radius = 4.5.dp.toPx(), center = Offset(size.width, lastY))
        }
    }
}

@Composable
private fun TopMerchantsCard(snap: AnalyticsSnapshot, currency: String) {
    StickerCard(Modifier.fillMaxWidth()) {
        Text("TOP MERCHANTS", fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp, color = MutedInk)
        if (snap.topMerchants.isEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text("No spending in this period.", fontSize = 11.5.sp, color = MutedInk)
        } else {
            val biggest = snap.topMerchants.maxOf { it.totalMinor }.coerceAtLeast(1L)
            snap.topMerchants.forEach { stat ->
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        stat.name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("×${stat.count}", fontSize = 9.5.sp, color = MutedInk)
                    Spacer(Modifier.width(8.dp))
                    Text(Money.format(stat.totalMinor, currency), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                }
                Spacer(Modifier.height(3.dp))
                Box(Modifier.fillMaxWidth().height(7.dp).background(Color(0xFFEFE9DA), RoundedCornerShape(4.dp))) {
                    Box(
                        Modifier
                            .fillMaxWidth((stat.totalMinor.toFloat() / biggest).coerceIn(0.02f, 1f))
                            .height(7.dp)
                            .background(Mustard, RoundedCornerShape(4.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun HowToCard() {
    StickerCard(Modifier.fillMaxWidth(), color = CardCream) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CandyChip("?", bg = Purple, pill = true)
            Spacer(Modifier.width(8.dp))
            Text("How to read this", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = Ink)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Pick a period and an account above — every card follows instantly. “Money out” is spending; transfers between your own accounts and balance corrections are excluded. Bars = daily spending (mustard marks your top day). The line is your balance trail. All of it is computed on this phone.",
            fontSize = 10.5.sp,
            color = MutedInk,
            lineHeight = 15.sp,
        )
    }
}

@Composable
private fun rememberCoroutineScopeSafe() = androidx.compose.runtime.rememberCoroutineScope()
