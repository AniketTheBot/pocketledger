package app.pocketledger.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.pocketledger.App
import app.pocketledger.MainActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Emulator E2E suite — the mobile equivalent of browser automation.
 *
 * Run with: ./gradlew :app:connectedDebugAndroidTest
 * (requires a running emulator or connected device; reports land in
 *  app/build/reports/androidTests/connected/)
 *
 * Each test starts from a clean install state (DB + prefs + learned model
 * wiped on-device) and drives the real UI.
 */
@RunWith(AndroidJUnit4::class)
class AppFlowTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val app: App
        get() = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as App

    private val c get() = app.container

    @Before
    fun resetState() {
        runBlocking { c.wipeEverything() }
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
    }

    /** Fast-path: skip onboarding by seeding an onboarded profile. */
    private fun seedOnboarded(balanceMinor: Long = 500_000): Long = runBlocking {
        val id = c.wallet.addAccount("Everyday", "CHECKING", "INR", balanceMinor, "1234")
        c.settings.setCurrency("INR")
        c.settings.setActiveAccount(id)
        c.settings.setOnboarded(true)
        id
    }

    /** Drives the complete real onboarding flow, then asserts exactly one account exists. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun onboarding_createsExactlyOneAccount() {
        rule.onNodeWithText("Get Started", substring = true).performClick()
        rule.onNode(hasSetTextAction()).performTextInput("QA")
        rule.onNodeWithText("Continue").performClick()
        rule.onNodeWithText("Continue").performClick()
        rule.onNodeWithText("Continue").performClick()

        repeat(4) { rule.onNodeWithText("0").performClick() }
        rule.onNodeWithText("Start tracking").performClick()
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithText("Not now", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Not now", substring = true).performClick()
        rule.waitForIdle()

        rule.onNodeWithText("YOUR BALANCE", substring = true).assertExists()
        val accounts = runBlocking { c.wallet.accounts() }
        assertEquals("onboarding must create exactly one account (regression: wipe left old rows behind)", 1, accounts.size)
    }

    /** Record an entry from scratch, then delete it from Home and verify the ledger. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun addEntry_thenDelete_removesItFromLedger() {
        seedOnboarded()
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()

        rule.onNode(hasText("Record", substring = true) and hasClickAction()).performClick()
        rule.onNodeWithText("1").performClick()
        rule.onNodeWithText("0").performClick()
        rule.onNodeWithText("0").performClick()
        rule.onNodeWithText("Save entry").performClick()
        rule.waitForIdle()

        val afterSave = runBlocking { c.wallet.allTxns() }
        assertEquals("entry should be recorded", 1, afterSave.size)
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithText("Transaction").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Transaction").assertExists()

        rule.onNodeWithText("Transaction").performClick()
        rule.onNodeWithText("Delete entry?", substring = true).assertExists()
        rule.onNodeWithText("Delete").performClick()

        runBlocking {
            withTimeout(5_000) {
                while (c.wallet.allTxns().isNotEmpty()) delay(100)
            }
        }
        assertTrue("entry should be gone after Delete", runBlocking { c.wallet.allTxns().isEmpty() })
    }

    /** Swiping the pager moves through every tab like a native gesture. */
    @Test
    fun pagerSwipe_movesThroughTabs() {
        seedOnboarded()
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()

        rule.onNodeWithTag("tab_pager").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.onNodeWithText("EVERYTHING TOGETHER", substring = true).assertExists()

        rule.onNodeWithTag("tab_pager").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.onNodeWithText("high-confidence", substring = true).assertExists()

        rule.onNodeWithTag("tab_pager").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.onNodeWithText("SMS INTELLIGENCE", substring = true).assertExists()
    }

    /** Erase everything must wipe DB + prefs and land back on onboarding. */
    @Test
    fun eraseEverything_resetsDatabaseAndOnboarding() {
        seedOnboarded()
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()

        rule.onNodeWithContentDescription("Settings").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Erase everything").performScrollTo().performClick()
        rule.onNode(hasSetTextAction()).performTextInput("DELETE")
        rule.onNodeWithText("Erase permanently").performClick()

        runBlocking {
            withTimeout(15_000) {
                while (true) {
                    val onboarded = c.settings.current().onboarded
                    val accounts = c.wallet.accounts()
                    if (!onboarded && accounts.isEmpty()) break
                    delay(200)
                }
            }
        }
        assertTrue("accounts must be gone after erase", runBlocking { c.wallet.accounts().isEmpty() })
        assertTrue("onboarding flag must be cleared", !runBlocking { c.settings.current().onboarded })
    }
}
