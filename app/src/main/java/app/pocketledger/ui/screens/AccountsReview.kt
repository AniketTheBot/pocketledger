package app.pocketledger.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.pocketledger.data.AppContainer
import app.pocketledger.data.AccountEntity
import app.pocketledger.data.STATUS_REVIEW
import app.pocketledger.data.SmsEntity
import app.pocketledger.money.Money
import app.pocketledger.ui.components.AmountChip
import app.pocketledger.ui.components.Avatar
import app.pocketledger.ui.components.CandyChip
import app.pocketledger.ui.components.AllClearArt
import app.pocketledger.ui.components.FilterChipSticker
import app.pocketledger.ui.components.InlineDivider
import app.pocketledger.ui.components.PillButton
import app.pocketledger.ui.components.SectionLabel
import app.pocketledger.ui.components.StickerCard
import app.pocketledger.ui.components.StickerTextField
import app.pocketledger.ui.theme.CardCream
import app.pocketledger.ui.theme.Cream
import app.pocketledger.ui.theme.Danger
import app.pocketledger.ui.theme.Green
import app.pocketledger.ui.theme.Ink
import app.pocketledger.ui.theme.Mustard
import app.pocketledger.ui.theme.MutedInk
import app.pocketledger.ui.theme.Pink
import app.pocketledger.ui.theme.Purple
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val AvatarTones = listOf(Mustard, Purple, Green, Pink)

@Composable
fun AccountsScreen(
    container: AppContainer,
    onAddAccount: () -> Unit,
    onSelectAccount: (Long) -> Unit,
) {
    val accounts by container.wallet.observeAccounts().collectAsState(initial = emptyList())
    val total by container.wallet.observeTotal().collectAsState(initial = 0L)
    val lastTxn by container.wallet.observeRecentTxns(1).collectAsState(initial = emptyList())
    var balances by remember { mutableStateOf<Map<Long, Long>>(emptyMap()) }
    var archiving by remember { mutableStateOf<AccountEntity?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(accounts, lastTxn) { balances = container.wallet.balances() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(10.dp))
        Text("Accounts", style = MaterialTheme.typography.headlineMedium, color = Ink)
        Spacer(Modifier.height(10.dp))
        StickerCard(Modifier.fillMaxWidth()) {
            Text("EVERYTHING TOGETHER", fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp, color = MutedInk)
            Spacer(Modifier.height(2.dp))
            Text(Money.format(total, "INR"), style = MaterialTheme.typography.displayLarge, color = Ink)
            Text("${accounts.count { !it.archived }} pockets · all currencies shown in ₹ equivalent formatting", fontSize = 10.sp, color = MutedInk, lineHeight = 14.sp)
        }
        Spacer(Modifier.height(14.dp))
        SectionLabel("Your accounts")
        Spacer(Modifier.height(6.dp))
        accounts.forEachIndexed { index, account ->
            StickerCard(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                onClick = { onSelectAccount(account.id) },
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(account.name.take(1).uppercase(), AvatarTones[index % AvatarTones.size], 38.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(account.name, fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp, color = Ink)
                        Text(
                            buildString {
                                append(account.type.lowercase().replaceFirstChar { it.uppercase() })
                                account.tail?.let { append(" · ••$it") }
                                append(" · ${account.currency}")
                                if (account.archived) append(" · archived")
                            },
                            fontSize = 10.sp,
                            color = MutedInk,
                        )
                    }
                    Text(
                        Money.format(balances[account.id] ?: account.openingBalanceMinor, account.currency),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        color = Ink,
                    )
                    Spacer(Modifier.width(8.dp))
                    if (account.archived) {
                        Text(
                            "Restore",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Ink,
                            modifier = Modifier.clickableNoRipple {
                                scope.launch { container.wallet.setArchived(account.id, false) }
                            },
                        )
                    } else {
                        Text(
                            "✕",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MutedInk,
                            modifier = Modifier.clickableNoRipple { archiving = account },
                        )
                    }
                }
            }
        }
        PillButton("＋ Add account", onAddAccount, Modifier.fillMaxWidth())
        Spacer(Modifier.height(120.dp))
    }

    archiving?.let { account ->
        Dialog(onDismissRequest = { archiving = null }) {
            StickerCard(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                Text("Archive \"${account.name}\"?", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Ink)
                Spacer(Modifier.height(6.dp))
                Text(
                    "It disappears from pickers and totals. Transactions are kept and you can restore it here any time.",
                    fontSize = 11.5.sp,
                    color = MutedInk,
                    lineHeight = 16.sp,
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("Keep", { archiving = null }, Modifier.weight(1f), bg = CardCream)
                    PillButton(
                        "Archive",
                        {
                            scope.launch { container.wallet.setArchived(account.id, true) }
                            archiving = null
                        },
                        Modifier.weight(1f),
                        bg = Danger,
                    )
                }
            }
        }
    }
}

@Composable
fun ReviewScreen(container: AppContainer, onEdit: (SmsEntity) -> Unit) {
    var pageSize by rememberSaveable { mutableIntStateOf(30) }
    val queue by container.wallet.observeReviewSms(pageSize).collectAsState(initial = null)
    val waiting by container.wallet.observeReviewCount().collectAsState(initial = 0)
    val accounts by container.wallet.observeAccounts().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val accountNames = remember(accounts) { accounts.associate { it.id to it.name } }
    val currencyById = remember(accounts) { accounts.associate { it.id to it.currency } }

    LaunchedEffect(listState, queue?.size, pageSize) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { last ->
                val size = queue?.size ?: 0
                if (size >= pageSize && pageSize < 1000 && last >= size - 2) {
                    pageSize += 30
                }
            }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Review", style = MaterialTheme.typography.headlineMedium, color = Ink)
                if (waiting > 0) {
                    CandyChip("$waiting waiting", bg = Mustard, pill = true)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Only high-confidence messages auto-record. Everything else waits here for you.",
                fontSize = 11.sp,
                color = MutedInk,
            )
        }

        val reviewItems = queue
        if (reviewItems == null) {
            items(2) { ReviewSkeletonCard() }
        } else if (reviewItems.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(top = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AllClearArt(Modifier.fillMaxWidth(0.56f))
                    Spacer(Modifier.height(6.dp))
                    Text("Nothing needs a look.", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = Ink)
                    Text(
                        "Parsed messages that need confirmation will appear here — fully offline.",
                        fontSize = 11.sp,
                        color = MutedInk,
                    )
                }
            }
        } else {
            items(reviewItems, key = { it.id }) { sms ->
                SmsReviewCard(
                    sms = sms,
                    accountName = sms.accountId?.let { accountNames[it] },
                    onConfirm = {
                        val accountId = sms.accountId
                        val amount = sms.amountMinor
                        val direction = sms.direction
                        if (accountId != null && amount != null && direction != null) {
                            val currency = currencyById[accountId] ?: "INR"
                            scope.launch {
                                container.pipeline.confirm(
                                    smsId = sms.id,
                                    accountId = accountId,
                                    direction = direction,
                                    amountMinor = amount,
                                    currency = currency,
                                    ts = sms.ts,
                                    merchant = sms.merchant,
                                    note = null,
                                )
                            }
                        }
                    },
                    onEdit = { onEdit(sms) },
                    onDiscard = { scope.launch { container.pipeline.discard(sms.id) } },
                    onAssignSender = {
                        val target = sms.accountId ?: accounts.firstOrNull()?.id
                        if (target != null) {
                            scope.launch { container.wallet.addRule(sms.senderNorm, target) }
                        }
                    },
                )
            }
        }
    }
}

/** Placeholder cards so Review paints instantly while the queue loads from Room. */
@Composable
private fun ReviewSkeletonCard() {
    StickerCard(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth(0.35f)
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFFEFE9DA))
        )
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFEFE9DA))
        )
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth(0.8f)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color(0xFFEFE9DA))
        )
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth(0.6f)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color(0xFFEFE9DA))
        )
    }
}

@Composable
private fun SmsReviewCard(
    sms: SmsEntity,
    accountName: String?,
    onConfirm: () -> Unit,
    onEdit: () -> Unit,
    onDiscard: () -> Unit,
    onAssignSender: () -> Unit,
) {
    val confidence = sms.confidence ?: 0
    val canConfirm = sms.accountId != null && sms.amountMinor != null && sms.direction != null
    StickerCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(sms.senderNorm, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(SimpleDateFormat("d MMM · HH:mm", Locale.getDefault()).format(Date(sms.ts)), fontSize = 9.5.sp, color = MutedInk)
            Spacer(Modifier.width(8.dp))
            CandyChip("$confidence", bg = when {
                confidence >= 85 -> Green
                confidence >= 50 -> Mustard
                else -> CardCream
            })
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(androidx.compose.ui.graphics.Color(0xFFEFE9DA))
                .padding(9.dp)
        ) {
            Text(sms.body, fontSize = 11.sp, color = MutedInk, lineHeight = 16.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(6.dp))
        FieldRow("Amount", sms.amountMinor?.let { Money.format(it, sms.currency ?: "INR") } ?: "—")
        FieldRow("Direction", sms.direction?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Ambiguous")
        FieldRow("Account", accountName ?: "Unassigned")
        sms.ref?.let { FieldRow("Ref", it) }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PillButton("Discard", onDiscard, Modifier.weight(0.9f), bg = CardCream)
            PillButton("Edit", onEdit, Modifier.weight(0.8f), bg = CardCream)
            PillButton("Confirm", onConfirm, Modifier.weight(1.1f), enabled = canConfirm)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Always record from ${sms.senderNorm} → ${accountName ?: "…"}",
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MutedInk,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun FieldRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 11.sp, color = MutedInk)
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
    }
}

@Composable
fun AddAccountDialog(container: AppContainer, onDismiss: () -> Unit) {
    val settings by container.settings.settings.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("CHECKING") }
    var tail by remember { mutableStateOf("") }
    var opening by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf(settings?.currency ?: "INR") }
    val types = listOf("CHECKING" to "Checking", "SAVINGS" to "Savings", "WALLET" to "Wallet", "CASH" to "Cash", "CREDIT" to "Credit")

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = Cream) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(Modifier.height(20.dp))
                Text("New account", style = MaterialTheme.typography.headlineMedium, color = Ink)
                Spacer(Modifier.height(12.dp))
                StickerTextField(name, { name = it }, "Name (e.g. Savings)")
                Spacer(Modifier.height(10.dp))
                Text("TYPE", fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, letterSpacing = 1.sp, color = MutedInk)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    types.take(3).forEach { (k, v) -> FilterChipSticker(v, type == k, { type = k }) }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    types.drop(3).forEach { (k, v) -> FilterChipSticker(v, type == k, { type = k }) }
                }
                Spacer(Modifier.height(10.dp))
                Text("CURRENCY", fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, letterSpacing = 1.sp, color = MutedInk)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("INR", "GBP", "USD", "EUR").forEach { c ->
                        FilterChipSticker(c, currency == c, { currency = c })
                    }
                }
                Spacer(Modifier.height(10.dp))
                StickerTextField(tail, { tail = it.filter(Char::isDigit).take(4) }, "Last 3–4 digits (optional)", numeric = true)
                Spacer(Modifier.height(10.dp))
                StickerTextField(opening, { opening = it }, "Opening balance, exact (e.g. 24861.15)", numeric = true)
                Spacer(Modifier.height(16.dp))
                PillButton(
                    "Add account",
                    onClick = {
                        val minor = Money.parseMinor(opening) ?: return@PillButton
                        if (name.isBlank()) return@PillButton
                        scope.launch {
                            container.wallet.addAccount(name, type, currency, minor, tail)
                            onDismiss()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = name.isNotBlank() && Money.parseMinor(opening) != null,
                )
                Spacer(Modifier.height(8.dp))
                PillButton("Cancel", onDismiss, Modifier.fillMaxWidth(), bg = CardCream)
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

/** Sticker design is flat: click without ripple visuals. */
@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this.clickable(interactionSource = interaction, indication = null, onClick = onClick)
}
