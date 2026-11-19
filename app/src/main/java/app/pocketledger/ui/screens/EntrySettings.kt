package app.pocketledger.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf as mutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.pocketledger.data.AppContainer
import app.pocketledger.data.SmsEntity
import app.pocketledger.money.Money
import app.pocketledger.ui.components.CandyChip
import app.pocketledger.ui.components.FilterChipSticker
import app.pocketledger.ui.components.MoneyKeypad
import app.pocketledger.ui.components.PillButton
import app.pocketledger.ui.components.SectionLabel
import app.pocketledger.ui.components.StickerCard
import app.pocketledger.ui.components.StickerTextField
import app.pocketledger.ui.theme.CardCream
import app.pocketledger.ui.theme.Cream
import app.pocketledger.ui.theme.Ink
import app.pocketledger.ui.theme.Mustard
import app.pocketledger.ui.theme.MutedInk
import app.pocketledger.ui.theme.Pink
import app.pocketledger.ui.theme.Purple
import kotlinx.coroutines.launch

@Composable
fun EntryDialog(container: AppContainer, prefillSms: SmsEntity?, onDismiss: () -> Unit) {
    val allAccounts by container.wallet.observeAccounts().collectAsState(initial = emptyList())
    val accounts = allAccounts.filter { !it.archived }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var direction by remember {
        mutableStateOf(if (prefillSms?.direction == "CREDIT") "IN" else "OUT")
    }
    var fromId by remember {
        mutableStateOf(prefillSms?.accountId ?: accounts.firstOrNull()?.id ?: -1L)
    }
    var toId by remember { mutableStateOf(accounts.drop(1).firstOrNull()?.id ?: -1L) }
    var amountText by remember {
        mutableStateOf(prefillSms?.amountMinor?.let { Money.toDecimal(it) } ?: "")
    }
    var merchant by remember { mutableStateOf(prefillSms?.merchant ?: "") }
    var saving by remember { mutableStateOf(false) }

    val parsed = Money.parseMinor(amountText)
    val fromAccount = accounts.firstOrNull { it.id == fromId }
    val toAccount = accounts.firstOrNull { it.id == toId }
    val currency = fromAccount?.currency ?: "INR"
    val transferCurrencyMismatch = direction == "TRANSFER" && toAccount != null && toAccount.currency != fromAccount?.currency
    val canSave = parsed != null && fromAccount != null && amountText.isNotBlank() &&
        (direction != "TRANSFER" || (toId != fromId && toAccount != null && !transferCurrencyMismatch)) &&
        !saving

    Dialog(
        onDismissRequest = { if (!saving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = Cream) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (prefillSms != null) "Confirm entry" else "New entry",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Ink,
                    )
                    Text(
                        "Close",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MutedInk,
                        modifier = Modifier.clickableNoRipple { onDismiss() },
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    FilterChipSticker("− Out", direction == "OUT", { direction = "OUT" })
                    FilterChipSticker("+ In", direction == "IN", { direction = "IN" })
                    if (prefillSms == null) {
                        FilterChipSticker("⇄ Transfer", direction == "TRANSFER", { direction = "TRANSFER" })
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("ACCOUNT", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, color = MutedInk)
                Spacer(Modifier.height(4.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    accounts.forEach { account ->
                        FilterChipSticker(account.name, fromId == account.id, { fromId = account.id })
                    }
                }
                if (direction == "TRANSFER") {
                    Spacer(Modifier.height(8.dp))
                    Text("TO", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, color = MutedInk)
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        accounts.filter { it.id != fromId }.forEach { account ->
                            FilterChipSticker(account.name, toId == account.id, { toId = account.id })
                        }
                    }
                    if (transferCurrencyMismatch) {
                        Spacer(Modifier.height(4.dp))
                        Text("Transfers need matching currencies.", fontSize = 10.sp, color = MutedInk)
                    }
                }
                Spacer(Modifier.height(10.dp))
                StickerCard(Modifier.fillMaxWidth()) {
                    Text(
                        if (direction == "IN") "MONEY IN" else if (direction == "TRANSFER") "MOVE" else "MONEY OUT",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp,
                        color = MutedInk,
                    )
                    Text(
                        parsed?.let { Money.format(it, currency) } ?: (Money.symbolFor(currency) + "0"),
                        style = MaterialTheme.typography.displayLarge,
                        color = Ink,
                    )
                }
                Spacer(Modifier.height(8.dp))
                MoneyKeypad(onKey = { amountText = applyKey(amountText, it) })
                Spacer(Modifier.height(8.dp))
                StickerTextField(merchant, { merchant = it }, "Merchant / note (optional)")
                Spacer(Modifier.weight(1f))
                PillButton(
                    text = when {
                        saving -> "Saving…"
                        prefillSms != null -> "Confirm & record"
                        direction == "TRANSFER" -> "Move money"
                        else -> "Save entry"
                    },
                    onClick = {
                        val minor = Money.parseMinor(amountText) ?: return@PillButton
                        if (fromAccount == null) return@PillButton
                        saving = true
                        scope.launch {
                            try {
                                when {
                                    prefillSms != null -> container.pipeline.confirm(
                                        smsId = prefillSms.id,
                                        accountId = fromAccount.id,
                                        direction = if (direction == "IN") "CREDIT" else "DEBIT",
                                        amountMinor = minor,
                                        currency = fromAccount.currency,
                                        ts = prefillSms.ts,
                                        merchant = merchant.ifBlank { null },
                                        note = null,
                                    )
                                    direction == "TRANSFER" && toAccount != null -> container.money.transfer(
                                        fromAccountId = fromAccount.id,
                                        toAccountId = toAccount.id,
                                        amountMinor = minor,
                                        currency = fromAccount.currency,
                                        note = merchant.ifBlank { null },
                                    )
                                    else -> container.money.addManual(
                                        accountId = fromAccount.id,
                                        direction = if (direction == "IN") "CREDIT" else "DEBIT",
                                        amountMinor = minor,
                                        currency = fromAccount.currency,
                                        merchant = merchant.ifBlank { null },
                                    )
                                }
                                app.pocketledger.widget.BalanceWidget.refresh(context)
                                onDismiss()
                            } finally {
                                saving = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canSave,
                )
            }
        }
    }
}

@Composable
fun ReconcileDialog(container: AppContainer, onDismiss: () -> Unit) {
    val accounts by container.wallet.observeAccounts().collectAsState(initial = emptyList())
    val settings by container.settings.settings.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var selectedId by remember {
        mutableStateOf(
            settings?.activeAccountId?.takeIf { id -> accounts.any { it.id == id } }
                ?: accounts.firstOrNull { !it.archived }?.id
                ?: -1L
        )
    }
    var amountText by remember { mutableStateOf("") }
    var current by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(selectedId, accounts) {
        current = if (selectedId > 0) container.wallet.balance(selectedId) else null
    }

    val account = accounts.firstOrNull { it.id == selectedId }
    val parsed = Money.parseMinor(amountText)
    val diff = if (parsed != null && current != null) parsed - current!! else null
    val currency = account?.currency ?: "INR"

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = Cream) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Reconcile", style = MaterialTheme.typography.headlineMedium, color = Ink)
                    Text("Close", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = MutedInk, modifier = Modifier.clickableNoRipple(onDismiss))
                }
                Spacer(Modifier.height(6.dp))
                Text("Type what your bank app says, exactly. The difference becomes one adjustment entry.", fontSize = 11.sp, color = MutedInk, lineHeight = 16.sp)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    accounts.filter { !it.archived }.forEach { acc ->
                        FilterChipSticker(acc.name, selectedId == acc.id, { selectedId = acc.id })
                    }
                }
                Spacer(Modifier.height(10.dp))
                StickerCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("App says", fontSize = 11.sp, color = MutedInk)
                        Text(current?.let { Money.format(it, currency) } ?: "—", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = Ink)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        parsed?.let { Money.format(it, currency) } ?: (Money.symbolFor(currency) + "0"),
                        style = MaterialTheme.typography.displayLarge,
                        color = Ink,
                    )
                    diff?.let {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Difference", fontSize = 11.sp, color = MutedInk)
                            CandyChip(
                                (if (it >= 0) "+" else "−") + Money.format(kotlin.math.abs(it), currency),
                                bg = if (it >= 0) app.pocketledger.ui.theme.Green else Pink,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                MoneyKeypad(onKey = { amountText = applyKey(amountText, it) })
                Spacer(Modifier.weight(1f))
                PillButton(
                    "Apply adjustment",
                    onClick = {
                        val minor = Money.parseMinor(amountText) ?: return@PillButton
                        if (selectedId <= 0) return@PillButton
                        scope.launch {
                            container.money.reconcile(selectedId, minor)
                            app.pocketledger.widget.BalanceWidget.refresh(context)
                            onDismiss()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = parsed != null && diff != null && diff != 0L,
                )
                Spacer(Modifier.height(8.dp))
                PillButton("Cancel", onDismiss, Modifier.fillMaxWidth(), bg = CardCream)
            }
        }
    }
}

@Composable
fun ScanDialog(container: AppContainer, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var scanning by remember { mutableStateOf(true) }
    var scanned by remember { mutableStateOf(0) }
    var total by remember { mutableStateOf(0) }
    var summary by remember { mutableStateOf<app.pocketledger.sms.ScanSummary?>(null) }
    val hasPermission = app.pocketledger.sms.SmsPermissions.granted(context)

    LaunchedEffect(Unit) {
        if (hasPermission) {
            summary = container.scanner.scan { s, t ->
                scanned = s
                total = t
            }
        }
        scanning = false
    }

    Dialog(
        onDismissRequest = { if (!scanning) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = Cream) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(40.dp))
                Text(if (scanning) "Scanning your inbox…" else "Scan complete", style = MaterialTheme.typography.headlineMedium, color = Ink)
                Spacer(Modifier.height(12.dp))
                if (!hasPermission) {
                    Text("SMS permission is off. Enable it in system settings to scan.", fontSize = 12.sp, color = MutedInk, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                } else if (scanning) {
                    Text("$scanned / ${if (total > 0) total else "?"}", style = MaterialTheme.typography.displayLarge, color = Ink)
                    Spacer(Modifier.height(6.dp))
                    Text("On-device only — nothing leaves this phone.", fontSize = 11.sp, color = MutedInk)
                } else {
                    summary?.let { s ->
                        StickerCard(Modifier.fillMaxWidth()) {
                            StatLine("Messages scanned", "${s.scanned}")
                            StatLine("Recorded", "${s.processed}")
                            StatLine("Needs review", "${s.review}")
                            StatLine("Ignored", "${s.ignored}")
                            StatLine("Duplicates skipped", "${s.duplicates}")
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                PillButton("Done", onDismiss, Modifier.fillMaxWidth(), enabled = !scanning)
            }
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = MutedInk)
        Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = Ink)
    }
}

@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this.clickable(interactionSource = interaction, indication = null, onClick = onClick)
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
