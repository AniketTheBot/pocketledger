package app.pocketledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.pocketledger.data.AppContainer
import app.pocketledger.data.SmsEntity
import app.pocketledger.ui.components.BottomPillNav
import app.pocketledger.ui.screens.AccountsScreen
import app.pocketledger.ui.screens.AddAccountDialog
import app.pocketledger.ui.screens.AnalyzerDialog
import app.pocketledger.ui.screens.EntryDialog
import app.pocketledger.ui.screens.HomeScreen
import app.pocketledger.ui.screens.OnboardingFlow
import app.pocketledger.ui.screens.ReconcileDialog
import app.pocketledger.ui.screens.ReviewScreen
import app.pocketledger.ui.screens.ScanDialog
import app.pocketledger.ui.screens.SettingsScreen
import app.pocketledger.ui.theme.Cream
import app.pocketledger.ui.theme.Ink
import app.pocketledger.ui.theme.Mustard
import app.pocketledger.ui.theme.PocketLedgerTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as App).container
        setContent {
            PocketLedgerTheme {
                Root(container)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        app.pocketledger.widget.BalanceWidget.refresh(this)
    }
}

@Composable
private fun Root(container: AppContainer) {
    val settings by container.settings.settings.collectAsState(initial = null)
    Surface(Modifier.fillMaxSize(), color = Cream) {
        when {
            settings == null -> Box(Modifier.fillMaxSize().background(Cream))
            settings?.onboarded != true -> OnboardingFlow(container) {}
            else -> MainShell(container)
        }
    }
}

@Composable
private fun MainShell(container: AppContainer) {
    var tab by rememberSaveable { mutableStateOf("home") }
    var showEntry by remember { mutableStateOf(false) }
    var editingSms by remember { mutableStateOf<SmsEntity?>(null) }
    var showReconcile by remember { mutableStateOf(false) }
    var showAddAccount by remember { mutableStateOf(false) }
    var showScan by remember { mutableStateOf(false) }
    var showAnalyzer by remember { mutableStateOf(false) }
    val reviewCount by container.wallet.observeReviewCount().collectAsState(initial = 0)
    val scope = rememberCoroutineScope()
    val saveableStateHolder = rememberSaveableStateHolder()

    Scaffold(
        containerColor = Cream,
        bottomBar = {
            BottomPillNav(
                selected = tab,
                onSelect = { tab = it },
                reviewCount = reviewCount,
            )
        },
        floatingActionButton = {
            if (tab == "home" || tab == "accounts") {
                FloatingActionButton(
                    onClick = {
                        editingSms = null
                        showEntry = true
                    },
                    containerColor = Mustard,
                    contentColor = Ink,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.padding(bottom = 6.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add entry")
                }
            }
        },
    ) { padding ->
        val tabOrder = listOf("home", "accounts", "review", "settings")
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .swipeToChangeTab(
                    onSwipeLeft = {
                        val i = tabOrder.indexOf(tab)
                        if (i in 0 until tabOrder.lastIndex) tab = tabOrder[i + 1]
                    },
                    onSwipeRight = {
                        val i = tabOrder.indexOf(tab)
                        if (i > 0) tab = tabOrder[i - 1]
                    },
                )
        ) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val dir = if (tabOrder.indexOf(targetState) >= tabOrder.indexOf(initialState)) 1 else -1
                    val spec = tween<IntOffset>(300, easing = FastOutSlowInEasing)
                    (slideInHorizontally(spec) { full -> dir * full } + fadeIn(tween(90))) togetherWith
                        (slideOutHorizontally(spec) { full -> -dir * full } + fadeOut(tween(90)))
                },
                label = "tab",
            ) { current ->
                saveableStateHolder.SaveableStateProvider(current) {
                when (current) {
                    "home" -> HomeScreen(
                        container = container,
                        onAddEntry = {
                            editingSms = null
                            showEntry = true
                        },
                        onScan = { showScan = true },
                        onReconcile = { showReconcile = true },
                        onAccounts = { tab = "accounts" },
                        onReview = { tab = "review" },
                        onAnalyze = { showAnalyzer = true },
                        onOpenSettings = { tab = "settings" },
                    )
                    "accounts" -> AccountsScreen(
                        container = container,
                        onAddAccount = { showAddAccount = true },
                        onSelectAccount = { id ->
                            scope.launch { container.settings.setActiveAccount(id) }
                            tab = "home"
                        },
                    )
                    "review" -> ReviewScreen(
                        container = container,
                        onEdit = { sms ->
                            editingSms = sms
                            showEntry = true
                        },
                    )
                    "settings" -> SettingsScreen(
                        container = container,
                        onScan = { showScan = true },
                        onReconcile = { showReconcile = true },
                    )
                }
                }
            }
        }
    }

    if (showEntry) {
        EntryDialog(container, editingSms) {
            showEntry = false
            editingSms = null
        }
    }
    if (showReconcile) {
        ReconcileDialog(container) { showReconcile = false }
    }
    if (showAddAccount) {
        AddAccountDialog(container) { showAddAccount = false }
    }
    if (showScan) {
        ScanDialog(container) { showScan = false }
    }
    if (showAnalyzer) {
        AnalyzerDialog(container) { showAnalyzer = false }
    }
}

/** Horizontal swipe anywhere in the tab content moves to the neighbouring tab. */
private fun Modifier.swipeToChangeTab(
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
): Modifier = pointerInput(onSwipeLeft, onSwipeRight) {
    var totalDrag = 0f
    detectHorizontalDragGestures(
        onDragStart = { totalDrag = 0f },
        onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
        onDragEnd = {
            if (totalDrag <= -90.dp.toPx()) onSwipeLeft()
            else if (totalDrag >= 90.dp.toPx()) onSwipeRight()
        },
    )
}
