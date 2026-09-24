package eu.darken.capod.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Emulator images without the Play Store have no billing, so this covers only that case. */
@RunWith(AndroidJUnit4::class)
class UpgradeScreenTest {

    private val app = CapodApp()

    @get:Rule
    val failureCapture = FailureCapture(app.device)

    @Before
    fun startAsNewUser() {
        app.resetToFirstRunState()
        app.launch()
        app.completeOnboarding()
    }

    @Test
    fun upgradeScreenExplainsThatPricesAreUnavailable() {
        app.click(app.desc("general_upgrade_action"))
        app.await(app.text("upgrades_gplay_unavailable_error"), BILLING_TIMEOUT_MS)
        app.click(app.text("general_dismiss_action"))
        app.scrollTo(app.text("upgrade_screen_offers_unavailable_title"))
        app.await(app.text("general_retry_action"))
    }

    companion object {
        // The billing connection has to give up first.
        private const val BILLING_TIMEOUT_MS = 60_000L
    }
}
