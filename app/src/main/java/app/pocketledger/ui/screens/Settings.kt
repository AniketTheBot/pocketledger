package app.pocketledger.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.pocketledger.data.AppContainer
import app.pocketledger.MainActivity
import app.pocketledger.sms.SmsPermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.pocketledger.ui.components.CandyChip
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
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    container: AppContainer,
    onScan: () -> Unit,
    onReconcile: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by container.settings.settings.collectAsState(initial = null)
    val rules by container.wallet.observeSenderRules().collectAsState(initial = emptyList())
    val accounts by container.wallet.observeAccounts().collectAsState(initial = emptyList())
    var counts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var exportMsg by remember { mutableStateOf<String?>(null) }
    var showWipe by remember { mutableStateOf(false) }
    var wipeText by remember { mutableStateOf("") }

    val granted = remember { SmsPermissions.granted(context) }
    LaunchedEffect(Unit) { counts = container.wallet.setSmsStatusCounts() }

    val jsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch {
                val data = buildExportJson(container)
                context.contentResolver.openOutputStream(uri)?.use { it.write(data.toByteArray()) }
                exportMsg = "JSON exported ✓"
            }
        }
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            scope.launch {
                val data = buildExportCsv(container)
                context.contentResolver.openOutputStream(uri)?.use { it.write(data.toByteArray()) }
                exportMsg = "CSV exported ✓"
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(10.dp))
        Text("Settings", style = MaterialTheme.typography.headlineMedium, color = Ink)
        Spacer(Modifier.height(12.dp))

        SectionLabel("SMS intelligence")
        Spacer(Modifier.height(6.dp))
        StickerCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("SMS scanning", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = Ink)
                CandyChip(if (granted) "granted" else "off", bg = if (granted) app.pocketledger.ui.theme.Green else CardCream, pill = true)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Processed ${counts["PROCESSED"] ?: 0} · Review ${counts["REVIEW"] ?: 0} · Ignored ${counts["IGNORED"] ?: 0}",
                fontSize = 11.sp, color = MutedInk,
            )
            Spacer(Modifier.height(10.dp))
            Stepper(
                label = "Auto-record at confidence",
                value = settings?.autoThreshold ?: 85,
                min = 60, max = 100, step = 5,
                onChange = { scope.launch { container.settings.setThresholds(it, settings?.reviewThreshold ?: 50) } },
            )
            Spacer(Modifier.height(6.dp))
            Stepper(
                label = "Send to review at confidence",
                value = settings?.reviewThreshold ?: 50,
                min = 10, max = 80, step = 5,
                onChange = { scope.launch { container.settings.setThresholds(settings?.autoThreshold ?: 85, it) } },
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Scan now", onScan, Modifier.weight(1f))
                PillButton("Reconcile", onReconcile, Modifier.weight(1f), bg = CardCream)
            }
        }

        Spacer(Modifier.height(14.dp))
        SectionLabel("Sender rules")
        Spacer(Modifier.height(6.dp))
        StickerCard(Modifier.fillMaxWidth()) {
            if (rules.isEmpty()) {
                Text("No rules yet. Rules are created from Review (\"always record from …\").", fontSize = 11.5.sp, color = MutedInk, lineHeight = 16.sp)
            } else {
                val names = accounts.associate { it.id to it.name }
                rules.forEach { rule ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(rule.pattern, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = Ink)
                        Text(" → ${rule.accountId?.let { names[it] } ?: "unassigned"}", fontSize = 11.sp, color = MutedInk, modifier = Modifier.weight(1f))
                        Text("✕", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = MutedInk, modifier = Modifier.clickable { scope.launch { container.wallet.deleteRule(rule.id) } })
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        SectionLabel("Your data")
        Spacer(Modifier.height(6.dp))
        StickerCard(Modifier.fillMaxWidth()) {
            Text("Everything lives in this app's private storage. Export is the only way data leaves — and only when you tap it.", fontSize = 11.5.sp, color = MutedInk, lineHeight = 16.sp)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Export JSON", { jsonLauncher.launch("pocketledger-export.json") }, Modifier.weight(1f), bg = CardCream)
                PillButton("Export CSV", { csvLauncher.launch("pocketledger-export.csv") }, Modifier.weight(1f), bg = CardCream)
            }
            exportMsg?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
            }
            Spacer(Modifier.height(10.dp))
            PillButton("Erase everything", { showWipe = true }, Modifier.fillMaxWidth(), bg = Pink)
        }

        Spacer(Modifier.height(14.dp))
        SectionLabel("Privacy")
        Spacer(Modifier.height(6.dp))
        StickerCard(Modifier.fillMaxWidth()) {
            Text(
                "PocketLedger has no INTERNET permission. Your messages are read and classified on this phone by a bundled model; nothing is uploaded, ever. The app works fully in airplane mode.",
                fontSize = 11.5.sp, color = MutedInk, lineHeight = 17.sp,
            )
        }

        Spacer(Modifier.height(14.dp))
        Text("PocketLedger 0.2.0 · Design 5 Sticker Ledger · offline by design", fontSize = 10.sp, color = MutedInk)
        Spacer(Modifier.height(130.dp))
    }

    if (showWipe) {
        Dialog(onDismissRequest = { showWipe = false }) {
            Surface(shape = RoundedCornerShape(20.dp), color = Cream, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("Erase everything?", style = MaterialTheme.typography.headlineMedium, color = Ink)
                    Spacer(Modifier.height(6.dp))
                    Text("This erases all app storage — accounts, transactions, scanned messages, learned patterns and settings — then restarts PocketLedger from onboarding. Type DELETE to confirm.", fontSize = 11.5.sp, color = MutedInk, lineHeight = 16.sp)
                    Spacer(Modifier.height(10.dp))
                    StickerTextField(wipeText, { wipeText = it }, "Type DELETE")
                    Spacer(Modifier.height(12.dp))
                    PillButton(
                        "Erase permanently",
                        onClick = {
                            container.appScope.launch {
                                container.wipeEverything()
                                withContext(Dispatchers.Main) {
                                    context.startActivity(
                                        Intent(context, MainActivity::class.java)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        bg = Pink,
                        enabled = wipeText == "DELETE",
                    )
                    Spacer(Modifier.height(8.dp))
                    PillButton("Cancel", { showWipe = false }, Modifier.fillMaxWidth(), bg = CardCream)
                }
            }
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, min: Int, max: Int, step: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 11.5.sp, color = MutedInk, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CandyChip("−", bg = CardCream, pill = true, modifier = Modifier.clickable { onChange((value - step).coerceAtLeast(min)) })
            Text("$value", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = Ink)
            CandyChip("+", bg = CardCream, pill = true, modifier = Modifier.clickable { onChange((value + step).coerceAtMost(max)) })
        }
    }
}

private suspend fun buildExportJson(container: AppContainer): String {
    val accounts = container.wallet.accounts()
    val txns = container.wallet.allTxns()
    val sms = container.wallet.allSms()
    val settings = container.settings.current()

    val root = JSONObject()
    root.put("app", "PocketLedger")
    root.put("schema", 1)
    root.put("exportedAt", System.currentTimeMillis())

    val accountArray = JSONArray()
    accounts.forEach { a ->
        accountArray.put(
            JSONObject()
                .put("id", a.id)
                .put("name", a.name)
                .put("type", a.type)
                .put("currency", a.currency)
                .put("openingBalanceMinor", a.openingBalanceMinor)
                .put("tail", a.tail ?: JSONObject.NULL)
                .put("archived", a.archived)
        )
    }
    root.put("accounts", accountArray)

    val txnArray = JSONArray()
    txns.forEach { t ->
        txnArray.put(
            JSONObject()
                .put("id", t.id)
                .put("accountId", t.accountId)
                .put("direction", t.direction)
                .put("amountMinor", t.amountMinor)
                .put("currency", t.currency)
                .put("ts", t.ts)
                .put("merchant", t.merchant ?: JSONObject.NULL)
                .put("note", t.note ?: JSONObject.NULL)
                .put("source", t.source)
                .put("kind", t.kind)
                .put("ref", t.ref ?: JSONObject.NULL)
                .put("transferId", t.transferId ?: JSONObject.NULL)
                .put("confidence", t.confidence ?: JSONObject.NULL)
                .put("smsId", t.smsId ?: JSONObject.NULL)
        )
    }
    root.put("txns", txnArray)

    val smsArray = JSONArray()
    sms.forEach { s ->
        smsArray.put(
            JSONObject()
                .put("id", s.id)
                .put("sender", s.sender)
                .put("body", s.body)
                .put("ts", s.ts)
                .put("status", s.status)
                .put("junk", s.junk ?: JSONObject.NULL)
                .put("direction", s.direction ?: JSONObject.NULL)
                .put("amountMinor", s.amountMinor ?: JSONObject.NULL)
                .put("currency", s.currency ?: JSONObject.NULL)
                .put("accountId", s.accountId ?: JSONObject.NULL)
                .put("confidence", s.confidence ?: JSONObject.NULL)
                .put("reason", s.reason ?: JSONObject.NULL)
        )
    }
    root.put("sms", smsArray)
    root.put(
        "settings",
        JSONObject()
            .put("currency", settings.currency)
            .put("autoThreshold", settings.autoThreshold)
            .put("reviewThreshold", settings.reviewThreshold)
    )
    return root.toString(2)
}

private suspend fun buildExportCsv(container: AppContainer): String {
    val accounts = container.wallet.accounts().associateBy { it.id }
    val txns = container.wallet.allTxns()
    val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
    val sb = StringBuilder()
    sb.append("date,account,direction,amount,currency,merchant,note,source,kind,ref,transferId,confidence\n")
    txns.forEach { t ->
        sb.append(csv(fmt.format(Date(t.ts)))).append(',')
        sb.append(csv(accounts[t.accountId]?.name ?: "Account ${t.accountId}")).append(',')
        sb.append(t.direction).append(',')
        sb.append(app.pocketledger.money.Money.toDecimal(t.amountMinor)).append(',')
        sb.append(t.currency).append(',')
        sb.append(csv(t.merchant)).append(',')
        sb.append(csv(t.note)).append(',')
        sb.append(t.source).append(',')
        sb.append(t.kind).append(',')
        sb.append(csv(t.ref)).append(',')
        sb.append(csv(t.transferId)).append(',')
        sb.append(t.confidence?.toString() ?: "")
        sb.append('\n')
    }
    return sb.toString()
}

private fun csv(value: String?): String {
    val s = value ?: ""
    return if (s.contains(',') || s.contains('"') || s.contains('\n')) {
        "\"" + s.replace("\"", "\"\"") + "\""
    } else s
}
