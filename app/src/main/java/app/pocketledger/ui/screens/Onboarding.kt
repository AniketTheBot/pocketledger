package app.pocketledger.ui.screens

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pocketledger.data.AppContainer
import app.pocketledger.money.Money
import app.pocketledger.sms.ScanSummary
import app.pocketledger.ui.components.CandyChip
import app.pocketledger.ui.components.FilterChipSticker
import app.pocketledger.ui.components.FlowerSticker
import app.pocketledger.ui.components.MoneyKeypad
import app.pocketledger.ui.components.PillButton
import app.pocketledger.ui.components.SectionLabel
import app.pocketledger.ui.components.StickerCard
import app.pocketledger.ui.components.StickerTextField
import app.pocketledger.ui.theme.CardCream
import app.pocketledger.ui.theme.Cream
import app.pocketledger.ui.theme.Green
import app.pocketledger.ui.theme.Ink
import app.pocketledger.ui.theme.Mustard
import app.pocketledger.ui.theme.MutedInk
import app.pocketledger.ui.theme.Pink
import app.pocketledger.ui.theme.Purple
import kotlinx.coroutines.launch

@Composable
fun OnboardingFlow(container: AppContainer, onFinished: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var userName by rememberSaveable { mutableStateOf("") }
    var currency by rememberSaveable { mutableStateOf("INR") }
    var name by rememberSaveable { mutableStateOf("Everyday") }
    var type by rememberSaveable { mutableStateOf("CHECKING") }
    var tail by rememberSaveable { mutableStateOf("") }
    var amountText by rememberSaveable { mutableStateOf("") }
    var accountId by rememberSaveable { mutableStateOf(-1L) }

    var scanning by remember { mutableStateOf(false) }
    var scanned by remember { mutableIntStateOf(0) }
    var scanTotal by remember { mutableIntStateOf(0) }
    var summary by remember { mutableStateOf<ScanSummary?>(null) }

    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[Manifest.permission.READ_SMS] == true) {
            step = 6
        } else {
            scope.launch {
                container.settings.setUserName(userName)
                container.settings.setOnboarded(true)
                container.settings.setActiveAccount(accountId)
                onFinished()
            }
        }
    }

    LaunchedEffect(step) {
        if (step == 6) {
            scanning = true
            summary = null
            val result = container.scanner.scan { s, t ->
                scanned = s
                scanTotal = t
            }
            summary = result
            scanning = false
        }
    }

    BackHandler(enabled = step in 1..5 && !scanning) { step -= 1 }

    Column(
        Modifier
            .fillMaxSize()
            .background(Cream)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        when (step) {
            0 -> WelcomeStep { step = 1 }
            1 -> NameStep(userName, { userName = it }) { step = 2 }
            2 -> CurrencyStep(currency) { currency = it; step = 3 }
            3 -> AccountStep(
                name = name, type = type, tail = tail,
                onName = { name = it }, onType = { type = it }, onTail = { tail = it },
                onNext = { step = 4 },
            )
            4 -> BalanceStep(
                currency = currency,
                amountText = amountText,
                onKey = { amountText = applyKey(amountText, it) },
                onNext = {
                    val minor = Money.parseMinor(amountText) ?: return@BalanceStep
                    scope.launch {
                        accountId = container.wallet.addAccount(name, type, currency, minor, tail)
                        container.settings.setCurrency(currency)
                        step = 5
                    }
                },
            )
            5 -> PermissionStep(
                onAllow = {
                    permissionLauncher.launch(
                        arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
                    )
                },
                onSkip = {
                    scope.launch {
                        container.settings.setUserName(userName)
                        container.settings.setOnboarded(true)
                        container.settings.setActiveAccount(accountId)
                        onFinished()
                    }
                },
            )
            6 -> ScanStep(
                scanning = scanning, scanned = scanned, total = scanTotal, summary = summary,
                onDone = {
                    scope.launch {
                        container.settings.setUserName(userName)
                        container.settings.setOnboarded(true)
                        container.settings.setActiveAccount(accountId)
                        onFinished()
                    }
                },
            )
        }
    }
}

private fun applyKey(current: String, key: String): String = when (key) {
    "⌫" -> current.dropLast(1)
    "." -> when {
        current.contains(".") -> current
        current.isEmpty() -> "0."
        else -> "$current."
    }
    else -> when {
        current.length >= 11 -> current
        current == "0" -> key
        else -> current + key
    }
}

private fun displayAmount(text: String, currency: String): String {
    val v = Money.parseMinor(text) ?: return Money.symbolFor(currency) + "0"
    return Money.format(v, currency)
}

@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text(
            "Set up\nyour wallet",
            style = MaterialTheme.typography.displayLarge,
            color = Ink,
            lineHeight = 34.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "PocketLedger reads your bank SMS on this phone only and logs every debit & credit — starting from your exact balance.",
            fontSize = 12.5.sp,
            color = MutedInk,
            lineHeight = 18.sp,
        )
        Box(Modifier.fillMaxWidth().height(310.dp)) {
            FlowerSticker("Scan bank SMS", Pink, 112.dp, Modifier.align(Alignment.TopStart).offset(x = 4.dp, y = 12.dp))
            FlowerSticker("Exact balance start", Mustard, 148.dp, Modifier.align(Alignment.TopEnd).offset(x = (-4).dp, y = 0.dp))
            FlowerSticker("Auto debit & credit log", Green, 132.dp, Modifier.align(Alignment.BottomStart).offset(x = 0.dp, y = (-20).dp))
            FlowerSticker("100% offline", Purple, 108.dp, Modifier.align(Alignment.BottomEnd).offset(x = (-8).dp, y = 0.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            listOf(0, 1, 2).forEach { i ->
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (i == 0) 9.dp else 8.dp)
                        .clip(CircleShape)
                        .background(if (i == 0) Ink else CardCream),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        PillButton("Get started", onNext, Modifier.fillMaxWidth())
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun StepHeader(stepLabel: String, title: String, body: String? = null) {
    SectionLabel(stepLabel)
    Spacer(Modifier.height(6.dp))
    Text(title, style = MaterialTheme.typography.headlineMedium, color = Ink)
    if (body != null) {
        Spacer(Modifier.height(6.dp))
        Text(body, fontSize = 12.5.sp, color = MutedInk, lineHeight = 18.sp)
    }
}

@Composable
private fun NameStep(name: String, onName: (String) -> Unit, onNext: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        StepHeader(
            "Step 1 of 4 · you",
            "What should we call you?",
            "Only used for the greeting on your home screen. It never leaves this phone.",
        )
        Spacer(Modifier.height(16.dp))
        StickerTextField(name, onName, "Your name")
        Spacer(Modifier.weight(1f))
        PillButton("Continue", onNext, Modifier.fillMaxWidth(), enabled = name.isNotBlank())
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun CurrencyStep(currency: String, onPick: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        StepHeader("Step 2 of 4 · currency", "Which currency?", "You can add accounts in other currencies later — each account keeps its own.")
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("INR", "GBP").forEach { c ->
                FilterChipSticker("${Money.symbolFor(c)}  $c", currency == c, { onPick(c) })
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("USD", "EUR").forEach { c ->
                FilterChipSticker("${Money.symbolFor(c)}  $c", currency == c, { onPick(c) })
            }
        }
        Spacer(Modifier.weight(1f))
        PillButton("Continue", { onPick(currency) }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun AccountStep(
    name: String,
    type: String,
    tail: String,
    onName: (String) -> Unit,
    onType: (String) -> Unit,
    onTail: (String) -> Unit,
    onNext: () -> Unit,
) {
    val types = listOf("CHECKING" to "Checking", "SAVINGS" to "Savings", "WALLET" to "Wallet", "CASH" to "Cash", "CREDIT" to "Credit")
    Column(Modifier.fillMaxSize()) {
        StepHeader("Step 3 of 4 · account", "Your first account", "The last digits help the scanner route bank SMS automatically.")
        Spacer(Modifier.height(16.dp))
        StickerTextField(name, onName, "Account name")
        Spacer(Modifier.height(12.dp))
        Text("TYPE", fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, letterSpacing = 1.sp, color = MutedInk)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            types.take(3).forEach { (key, label) ->
                FilterChipSticker(label, type == key, { onType(key) })
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            types.drop(3).forEach { (key, label) ->
                FilterChipSticker(label, type == key, { onType(key) })
            }
        }
        Spacer(Modifier.height(16.dp))
        StickerTextField(tail, { onTail(it.filter { ch -> ch.isDigit() }.take(4)) }, "Last 3–4 digits of a/c (optional)", numeric = true)
        Spacer(Modifier.weight(1f))
        PillButton("Continue", onNext, Modifier.fillMaxWidth(), enabled = name.isNotBlank())
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun BalanceStep(
    currency: String,
    amountText: String,
    onKey: (String) -> Unit,
    onNext: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        StepHeader("Step 4 of 4 · balance", "What’s your balance right now?", "Exact to the last digit — you can correct it any time with Reconcile.")
        Spacer(Modifier.height(18.dp))
        StickerCard(Modifier.fillMaxWidth()) {
            Text("EVERYDAY · CHECKING", fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp, color = MutedInk)
            Spacer(Modifier.height(4.dp))
            Text(displayAmount(amountText, currency), style = MaterialTheme.typography.displayLarge, color = Ink)
        }
        Spacer(Modifier.weight(1f))
        MoneyKeypad(onKey)
        Spacer(Modifier.height(12.dp))
        PillButton("Start tracking", onNext, Modifier.fillMaxWidth(), enabled = Money.parseMinor(amountText) != null)
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun PermissionStep(onAllow: () -> Unit, onSkip: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(20.dp))
        FlowerSticker("SMS", Green, 120.dp)
        Spacer(Modifier.height(18.dp))
        Text("Let PocketLedger read bank SMS?", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, color = Ink)
        Spacer(Modifier.height(10.dp))
        Text(
            "Transactions are detected on-device by a small built-in model. Messages never leave this phone — the app has no internet permission at all.",
            fontSize = 12.5.sp, color = MutedInk, lineHeight = 18.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        PillButton("Allow SMS scanning", onAllow, Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        PillButton("Not now — manual only", onSkip, Modifier.fillMaxWidth(), bg = CardCream)
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun ScanStep(
    scanning: Boolean,
    scanned: Int,
    total: Int,
    summary: ScanSummary?,
    onDone: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        StepHeader("Almost there", if (scanning) "Scanning your inbox…" else "Scan complete")
        Spacer(Modifier.height(14.dp))
        StickerCard(Modifier.fillMaxWidth()) {
            if (scanning) {
                Text("$scanned / ${if (total > 0) total else "?"}", style = MaterialTheme.typography.headlineMedium, color = Ink)
                Spacer(Modifier.height(6.dp))
                Text("Reading messages on this phone only.", fontSize = 12.sp, color = MutedInk)
            } else {
                val s = summary
                if (s == null) {
                    Text("Scanning unavailable — you can start manually.", fontSize = 12.5.sp, color = MutedInk)
                } else {
                    Text("${s.scanned} messages scanned", style = MaterialTheme.typography.headlineMedium, color = Ink)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CandyChip("${s.processed} recorded", bg = Green)
                        CandyChip("${s.review} review", bg = Purple)
                        CandyChip("${s.ignored} ignored", bg = CardCream)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Everything stayed on-device.", fontSize = 11.sp, color = MutedInk)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        PillButton("Open my ledger", onDone, Modifier.fillMaxWidth(), enabled = !scanning)
        Spacer(Modifier.height(18.dp))
    }
}
