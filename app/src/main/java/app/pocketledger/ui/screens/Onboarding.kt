package app.pocketledger.ui.screens

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.delay
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
            val result = container.scanner.scan(learningOnly = true) { s, t ->
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
            .padding(horizontal = 22.dp, vertical = 6.dp)
    ) {
        when (step) {
            0 -> WelcomeStep { step = 1 }
            1 -> NameStep(userName, { userName = it }) { step = 2 }
            2 -> CurrencyStep(
                currency = currency,
                onSelect = { currency = it },
                onNext = { step = 3 },
            )
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
                        // Everything before this instant is history, never ledger.
                        container.settings.setAnchorTs(System.currentTimeMillis())
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

/* ------------------------------------------------------------------ */
/* Steps — everything vertically centered, buttons pinned at bottom.   */
/* ------------------------------------------------------------------ */

@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    val headerAlpha = remember { Animatable(0f) }
    val headerScale = remember { Animatable(0.94f) }
    val headerOffsetY = remember { Animatable(240f) }
    val buttonAlpha = remember { Animatable(0f) }
    val buttonOffset = remember { Animatable(10f) }

    LaunchedEffect(Unit) {
        launch { headerAlpha.animateTo(1f, tween(280)) }
        launch { headerScale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 320f)) }
        launch {
            delay(780)
            headerOffsetY.animateTo(0f, tween(540, easing = FastOutSlowInEasing))
        }
        launch {
            delay(1800)
            buttonAlpha.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
            buttonOffset.animateTo(0f, tween(420, easing = FastOutSlowInEasing))
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = headerAlpha.value
                    translationY = headerOffsetY.value.dp.toPx()
                    scaleX = headerScale.value
                    scaleY = headerScale.value
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Set up\nyour wallet",
                style = MaterialTheme.typography.displayLarge,
                color = Ink,
                lineHeight = 36.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "PocketLedger reads your bank SMS on this phone only and logs every debit & credit — starting from your exact balance.",
                fontSize = 13.sp,
                color = MutedInk,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 10.dp),
            )
        }

        Spacer(Modifier.height(26.dp))
        Box(Modifier.fillMaxWidth().height(376.dp)) {
            AnimatedFlower(
                "Scan SMS", Pink, 126.dp,
                Modifier.align(Alignment.TopStart),
                delayMs = 980, petals = 9, depth = 6.5f,
            )
            AnimatedFlower(
                "Exact balance", Mustard, 170.dp,
                Modifier.align(Alignment.TopEnd).offset(x = (-2).dp, y = 6.dp),
                delayMs = 1120, petals = 12, depth = 7f, idleFloat = true,
            )
            AnimatedFlower(
                "Auto log", Green, 128.dp,
                Modifier.align(Alignment.BottomStart).offset(y = (-4).dp),
                delayMs = 1260, petals = 7, depth = 8f,
            )
            AnimatedFlower(
                "100% offline", Purple, 112.dp,
                Modifier.align(Alignment.BottomEnd).offset(x = (-6).dp, y = (-2).dp),
                delayMs = 1400, petals = 16, depth = 5.5f,
            )
        }

        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = buttonAlpha.value
                    translationY = buttonOffset.value.dp.toPx()
                }
        ) {
            PillButton("Get started", onNext, Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(18.dp))
    }
}

/** Sticker entrance: springy pop + un-tilt, then a slow ambient sway. */
@Composable
private fun AnimatedFlower(
    label: String,
    color: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    delayMs: Int = 0,
    petals: Int = 11,
    depth: Float = 7f,
    idleFloat: Boolean = false,
) {
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.72f) }
    val rotation = remember { Animatable(-6f) }
    val floatTransition = rememberInfiniteTransition(label = "flower-float")
    val floatY by floatTransition.animateFloat(
        initialValue = if (idleFloat) -3f else 0f,
        targetValue = if (idleFloat) 3f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "floatY",
    )

    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        launch { alpha.animateTo(1f, tween(220)) }
        launch { scale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 300f)) }
        launch { rotation.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 280f)) }
    }

    Box(
        modifier.graphicsLayer {
            this.alpha = alpha.value
            scaleX = scale.value
            scaleY = scale.value
            rotationZ = rotation.value
            translationY = floatY.dp.toPx()
        }
    ) {
        FlowerSticker(label, color, size, petals = petals, depth = depth)
    }
}

@Composable
private fun NameStep(name: String, onName: (String) -> Unit, onNext: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        SectionLabel("Step 1 of 4 · you")
        Spacer(Modifier.height(8.dp))
        Text(
            "What should we call you?",
            style = MaterialTheme.typography.headlineMedium,
            color = Ink,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Only used for the greeting on your home screen.\nIt never leaves this phone.",
            fontSize = 12.5.sp,
            color = MutedInk,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(26.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { if (it.length <= 24) onName(it) },
            singleLine = true,
            placeholder = {
                Text(
                    "Your name",
                    fontSize = 20.sp,
                    color = MutedInk.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            textStyle = MaterialTheme.typography.headlineMedium.copy(
                textAlign = TextAlign.Center,
                color = Ink,
            ),
            modifier = Modifier.fillMaxWidth().height(82.dp),
            shape = RoundedCornerShape(20.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Ink,
                unfocusedBorderColor = Ink,
                cursorColor = Ink,
                focusedContainerColor = CardCream,
                unfocusedContainerColor = CardCream,
            ),
        )
        Spacer(Modifier.weight(1f))
        PillButton("Continue", onNext, Modifier.fillMaxWidth(), enabled = name.isNotBlank())
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun CurrencyStep(currency: String, onSelect: (String) -> Unit, onNext: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.weight(0.55f))
        SectionLabel("Step 2 of 4 · currency")
        Spacer(Modifier.height(6.dp))
        Text("Which currency?", style = MaterialTheme.typography.headlineMedium, color = Ink)
        Spacer(Modifier.height(6.dp))
        Text(
            "You can add accounts in other currencies later — each account keeps its own.",
            fontSize = 12.5.sp,
            color = MutedInk,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(22.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CurrencyCard("INR", "₹", "Indian Rupee", currency == "INR", { onSelect("INR") }, Modifier.weight(1f))
                CurrencyCard("GBP", "£", "British Pound", currency == "GBP", { onSelect("GBP") }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CurrencyCard("USD", "$", "US Dollar", currency == "USD", { onSelect("USD") }, Modifier.weight(1f))
                CurrencyCard("EUR", "€", "Euro", currency == "EUR", { onSelect("EUR") }, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.weight(1f))
        PillButton("Continue", onNext, Modifier.fillMaxWidth())
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun CurrencyCard(
    code: String,
    symbol: String,
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StickerCard(
        modifier = modifier.height(112.dp),
        onClick = onClick,
        color = if (selected) Mustard else CardCream,
    ) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(symbol, style = MaterialTheme.typography.displayLarge, color = Ink)
            Text(code, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = Ink)
            Text(name, fontSize = 9.5.sp, color = MutedInk)
        }
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
        Spacer(Modifier.weight(0.55f))
        SectionLabel("Step 3 of 4 · account")
        Spacer(Modifier.height(6.dp))
        Text("Your first account", style = MaterialTheme.typography.headlineMedium, color = Ink)
        Spacer(Modifier.height(6.dp))
        Text(
            "The last 3–4 digits (optional) route SMS from this account automatically. Have more than one bank account? Add them later — unmatched messages go to Review, never guessed.",
            fontSize = 12.5.sp,
            color = MutedInk,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(18.dp))
        StickerTextField(name, onName, "Account name")
        Spacer(Modifier.height(14.dp))
        Text("TYPE", fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, letterSpacing = 1.sp, color = MutedInk)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            types.take(3).forEach { (key, label) -> FilterChipSticker(label, type == key, { onType(key) }) }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            types.drop(3).forEach { (key, label) -> FilterChipSticker(label, type == key, { onType(key) }) }
        }
        Spacer(Modifier.height(14.dp))
        StickerTextField(tail, { onTail(it.filter(Char::isDigit).take(4)) }, "Last 3–4 digits of a/c (optional)", numeric = true)
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
        Spacer(Modifier.weight(0.7f))
        SectionLabel("Step 4 of 4 · balance")
        Spacer(Modifier.height(6.dp))
        Text("What’s your balance right now?", style = MaterialTheme.typography.headlineMedium, color = Ink)
        Spacer(Modifier.height(6.dp))
        Text(
            "Exact to the last digit — you can correct it any time with Reconcile.",
            fontSize = 12.5.sp, color = MutedInk, lineHeight = 18.sp,
        )
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
        Spacer(Modifier.weight(0.8f))
        FlowerSticker("SMS", Green, 128.dp)
        Spacer(Modifier.height(20.dp))
        Text(
            "Let PocketLedger read bank SMS?",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            color = Ink,
        )
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
        Spacer(Modifier.weight(0.7f))
        SectionLabel("Almost there")
        Spacer(Modifier.height(6.dp))
        Text(
            if (scanning) "Scanning your inbox…" else "You’re all set",
            style = MaterialTheme.typography.headlineMedium,
            color = Ink,
        )
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
                    Text("${s.scanned} messages checked", style = MaterialTheme.typography.headlineMedium, color = Ink)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CandyChip("${s.learned} patterns learned", bg = Green)
                        CandyChip("${(s.scanned - s.learned).coerceAtLeast(0)} skipped", bg = CardCream)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "History never touches your balance. It only teaches the on-device model how your banks phrase money in and out. Tracking starts from the amount you entered — only new messages record from here on.",
                        fontSize = 11.sp,
                        color = MutedInk,
                        lineHeight = 16.sp,
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        PillButton("Open my ledger", onDone, Modifier.fillMaxWidth(), enabled = !scanning)
        Spacer(Modifier.height(18.dp))
    }
}
