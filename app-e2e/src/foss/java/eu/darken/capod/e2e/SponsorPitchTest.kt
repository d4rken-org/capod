package eu.darken.capod.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SponsorPitchTest {

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
    fun dashboardDonateActionOpensThePitch() {
        app.click(app.desc("general_donate_action"))
        app.scrollTo(app.text("upgrade_foss_sponsor_action"))
    }

    @Test
    fun lockedSettingOpensThePitch() {
        app.openSettings()
        app.click(app.text("settings_general_label"))
        app.click(app.text("ui_theme_mode_label"))
        app.scrollTo(app.text("upgrade_foss_sponsor_action"))
    }

    @Test
    fun upgradeStatusOffersThePitchToFreeUsers() {
        app.openUpgradeStatus()
        app.click(app.text("upgrade_screen_status_free_action"))
        app.scrollTo(app.text("upgrade_foss_sponsor_action"))
    }
}
