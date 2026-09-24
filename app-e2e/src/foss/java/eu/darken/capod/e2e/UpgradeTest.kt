package eu.darken.capod.e2e

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Sets up a user's state in an older release and checks it after an in-place upgrade. The two
 * phases run as separate instrumentation runs with the app swapped in between.
 */
@RunWith(AndroidJUnit4::class)
class UpgradeTest {

    private val app = CapodApp()

    @get:Rule
    val failureCapture = FailureCapture(app.device)

    @Test
    fun beforeUpgrade() {
        app.resetToFirstRunState()
        app.launch()
        app.completeOnboarding()
        app.grantDashboardPermissions()
        app.await(app.text("overview_monitoring_off_label"))

        app.click(app.desc("settings_devices_label"))
        app.click(app.text("profiles_name_default"))
        app.await(By.clazz(EDIT_TEXT).text(app.string("profiles_name_default"))).text = PROFILE_NAME
        app.click(By.clazz(EDIT_TEXT).text(UNKNOWN_MODEL))
        app.click(By.text(PROFILE_MODEL))
        app.click(app.desc("profiles_save_action"))
        app.await(app.desc("profiles_add_action"))
        app.device.pressBack()

        app.openUpgradeStatus()
        app.becomeSupporter()
        app.device.pressBack()

        app.click(app.text("settings_general_label"))
        app.click(app.text("ui_theme_mode_label"))
        app.click(app.text("ui_theme_mode_dark_label"))
        app.awaitGone(app.text("ui_theme_mode_system_label"))

        // Give the settings time to be written, then prove this build kept them before the upgrade
        // replaces it.
        SystemClock.sleep(SETTINGS_SAVE_SETTLE_MS)
        app.forceStop()
        app.launch()
        assertStateSurvived()
    }

    @Test
    fun afterUpgrade() {
        app.launch()
        assertStateSurvived()
    }

    private fun assertStateSurvived() {
        app.await(app.text("overview_monitoring_off_label"))
        app.assertAbsent(app.text("general_continue_action"))

        app.click(app.desc("settings_devices_label"))
        app.click(By.text(PROFILE_NAME))
        app.await(By.clazz(EDIT_TEXT).text(PROFILE_MODEL))
        app.device.pressBack()
        app.await(app.desc("profiles_add_action"))
        app.device.pressBack()

        app.openUpgradeStatus()
        app.await(app.text("upgrade_screen_status_upgraded_title"))
        app.await(app.formattedText("upgrade_foss_supporter_since"))
        app.device.pressBack()

        app.click(app.text("settings_general_label"))
        app.await(app.text("ui_theme_mode_dark_label"))
        app.device.pressBack()
        app.device.pressBack()

        app.awaitOverview()
        app.assertAbsent(app.desc("general_donate_action"))
    }

    companion object {
        private const val EDIT_TEXT = "android.widget.EditText"
        private const val PROFILE_NAME = "E2E Pods"
        // Model names are not translated.
        private const val UNKNOWN_MODEL = "Unknown"
        private const val PROFILE_MODEL = "AirPods (Gen 2)"
        private const val SETTINGS_SAVE_SETTLE_MS = 2_000L
    }
}
