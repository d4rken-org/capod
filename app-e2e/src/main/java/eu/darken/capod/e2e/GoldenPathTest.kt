package eu.darken.capod.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GoldenPathTest {

    private val app = CapodApp()

    @get:Rule
    val failureCapture = FailureCapture(app.device)

    @Before
    fun resetToFirstRunState() = app.resetToFirstRunState()

    @Test
    fun newUserGetsFromOnboardingToAWorkingDashboard() {
        app.launch()
        app.completeOnboarding()

        app.grantDashboardPermissions()
        // A fresh install gets a profile without a paired device, which never starts background
        // monitoring. The card saying so only shows once nothing blocks scanning.
        app.await(app.text("overview_monitoring_off_label"))

        app.forceStop()
        app.launch()
        app.await(app.text("overview_monitoring_off_label"))
        app.assertAbsent(app.text("general_continue_action"))
        app.assertAbsent(app.text("general_grant_permission_action"))
    }
}
