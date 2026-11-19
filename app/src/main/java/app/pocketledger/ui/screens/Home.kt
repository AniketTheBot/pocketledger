package app.pocketledger.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pocketledger.data.AppContainer
import app.pocketledger.data.AppSettings
import app.pocketledger.data.InOutTotals
import app.pocketledger.data.TxnEntity
import app.pocketledger.money.Money
import app.pocketledger.ui.components.AmountChip
import app.pocketledger.ui.components.Avatar
import app.pocketledger.ui.components.CandyChip
import app.pocketledger.ui.components.FilterChipSticker
import app.pocketledger.ui.components.InlineDivider
import app.pocketledger.ui.components.PillButton
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
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val PosText = Color(0xFF2F6B2A)
private val NegText = Color(0xFFA03050)
private val AvatarPalette = listOf(Mustard, Purple, Green, Pink)

@Composable
fun HomeScreen(
    container: AppContainer,
    onAddEntry: () -> Unit,
    onScan: () -> Unit,
    onReconcile: () -> Unit,
    onAccounts: () -> Unit,
    onReview: () -> Unit,
    onAnalyze: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val accounts by container.wallet.observeAccounts().collectAsState(initial = emptyList())
    val settings by container.settings.settings.collectAsState(initial = AppSettings())
    val recent by container.wallet.observeRecentTxns(40).collectAsState(initial = emptyList())
    val reviewCount by container.wallet.observeReviewCount().collectAsState(initial = 0)
    val total by container.wallet.observeTotal().collectAsState(initial = 0L)

    var balances by remember { mutableStateOf<Map<Long, Long>>(emptyMap()) }
    var inOut by remember { mutableStateOf(InOutTotals(0, 0)) }
    val scope = rememberCoroutineScope()

    val active = accounts.firstOrNull { it.id == settings.activeAccountId && !it.archived }
    val currency = active?.currency ?: settings.currency
    val balance = active?.let { balances[it.id] ?: it.openingBalanceMinor } ?: total

    LaunchedEffect(accounts, recent) { balances = container.wallet.balances() }

    val monthStart = remember {
        Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    LaunchedEffect(active?.id, recent.size, accounts.size) {
        inOut = container.wallet.inOutBetween(monthStart, System.currentTimeMillis() + 1, active?.id)
    }

    val visibleTxns = recent.filter { active == null || it.accountId == active.id }.take(8)
    val accountNames = accounts.associate { it.id to it.name }
    val lastScan = if (settings.lastScanTs > 0) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(settings.lastScanTs))
    } else null

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clickableNoRipple(onOpenSettings),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "Hello, ${settings.userName.ifBlank { "there" }} 👋",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp,
                    color = Ink,
                )
                Text(
                    lastScan?.let { "Last scan $it · on-device" } ?: "Ready when you are",
                    fontSize = 10.5.sp,
                    color = MutedInk,
                )
            }
            Avatar(settings.userName.firstOrNull()?.uppercaseChar()?.toString() ?: "A", Pink, 34.dp)
        }

        Spacer(Modifier.height(10.dp))

        StickerCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("YOUR BALANCE", fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp, color = MutedInk)
                Text(
                    active?.let { "${it.name}${it.tail?.let { t -> " ••$t" } ?: ""}" } ?: "All accounts",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MutedInk,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(Money.format(balance, currency), style = MaterialTheme.typography.displayLarge, color = Ink)
            Spacer(Modifier.height(2.dp))
            Row {
                Text("↑ in ${Money.format(inOut.credit, currency)}", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = PosText)
                Spacer(Modifier.width(10.dp))
                Text("↓ out ${Money.format(inOut.debit, currency)}", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = NegText)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("↗ Record", onAddEntry, Modifier.weight(1f))
                PillButton("↙ Scan SMS", onScan, Modifier.weight(1f), bg = Pink)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Reconcile balance",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MutedInk,
                modifier = Modifier
                    .padding(vertical = 2.dp)
                    .fillMaxWidth()
                    .padding(start = 2.dp)
                    .clickableNoRipple(onReconcile),
            )
        }

        if (reviewCount > 0) {
            Spacer(Modifier.height(12.dp))
            StickerCard(color = Purple, onClick = onReview, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("$reviewCount message${if (reviewCount == 1) "" else "s"} need a look", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = Ink)
                        Text("Confirm or discard in Review", fontSize = 10.5.sp, color = Ink.copy(alpha = .7f))
                    }
                    CustArrow()
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        StickerCard(color = Mustard, onClick = onAnalyze, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("SMART ANALYZER", fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp, color = Ink.copy(alpha = .7f))
                    Text("See where your money goes", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = Ink)
                    Text("Periods · accounts · trends", fontSize = 10.5.sp, color = Ink.copy(alpha = .65f))
                }
                Text("→", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Accounts")
            Text("See all", fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = MutedInk, modifier = Modifier.clickableNoRipple(onAccounts))
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            FilterChipSticker("All", active == null, {
                scope.launch { container.settings.setActiveAccount(-1L) }
            }, Modifier.padding(top = 8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar("＋", CardCream, 42.dp, Modifier.clickableNoRipple(onAccounts))
                Text("New", fontSize = 9.5.sp, color = MutedInk, modifier = Modifier.padding(top = 3.dp))
            }
            accounts.filter { !it.archived }.take(3).forEachIndexed { index, account ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(
                        account.name.take(1).uppercase(),
                        AvatarPalette[index % AvatarPalette.size],
                        42.dp,
                        Modifier.clickableNoRipple {
                            scope.launch { container.settings.setActiveAccount(account.id) }
                        },
                    )
                    Text(
                        account.name.take(8),
                        fontSize = 9.5.sp,
                        color = if (active?.id == account.id) Ink else MutedInk,
                        fontWeight = if (active?.id == account.id) FontWeight.ExtraBold else FontWeight.Medium,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        SectionLabel("Recent transactions")
        Spacer(Modifier.height(6.dp))

        if (visibleTxns.isEmpty()) {
            StickerCard(Modifier.fillMaxWidth()) {
                Text("Nothing recorded yet.", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = Ink)
                Spacer(Modifier.height(3.dp))
                Text("Your messages stay on this phone. Tap Record or Scan SMS to begin.", fontSize = 11.5.sp, color = MutedInk, lineHeight = 16.sp)
            }
        } else {
            StickerCard(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                visibleTxns.forEachIndexed { index, txn ->
                    TxnRow(txn, accountNames[txn.accountId] ?: "Account")
                    if (index != visibleTxns.lastIndex) InlineDivider()
                }
            }
        }

        Spacer(Modifier.height(120.dp))
    }
}

@Composable
private fun TxnRow(txn: TxnEntity, accountName: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            (txn.merchant ?: txn.note ?: accountName).take(1).uppercase(),
            AvatarPalette[(txn.id % AvatarPalette.size).toInt()],
            34.dp,
        )
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                txn.merchant ?: txn.note ?: if (txn.kind == "ADJUST") "Adjustment" else "Transaction",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 12.5.sp,
                color = Ink,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                "$accountName · ${SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(txn.ts))}",
                fontSize = 9.5.sp,
                color = MutedInk,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(6.dp))
        AmountChip(txn.direction, txn.amountMinor, txn.currency)
    }
}

@Composable
private fun CustArrow() {
    Text("→", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
}

/** Sticker design is flat: click without ripple visuals. */
@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this.clickable(interactionSource = interaction, indication = null, onClick = onClick)
}
