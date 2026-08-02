package eu.darken.capod.main.ui.overview

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import eu.darken.capod.R
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.common.compose.preview.MOCK_NOW
import eu.darken.capod.common.compose.preview.MockPodDataProvider
import eu.darken.capod.main.core.MonitorMode
import eu.darken.capod.pods.core.apple.PodModel
import org.junit.Assert.assertTrue
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

class OverviewScreenMonitoringTest : BaseComposeRobolectricTest() {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun state(
        effectiveMode: MonitorMode,
        address: String?,
    ) = OverviewViewModel.State(
        now = MOCK_NOW,
        permissions = emptySet(),
        devices = emptyList(),
        isDebug = false,
        isBluetoothEnabled = true,
        effectiveMode = effectiveMode,
        profiles = listOf(
            MockPodDataProvider.profile("My Headphones", PodModel.AIRPODS_PRO2, address = address),
        ),
        upgradeInfo = MockPodDataProvider.fossInfo(),
        showUnmatchedDevices = false,
    )

    @Test
    fun `MANUAL mode states that background monitoring is off`() {
        composeRule.setContent {
            PreviewWrapper {
                OverviewScreen(
                    state = state(effectiveMode = MonitorMode.MANUAL, address = null),
                    onRequestPermission = {},
                    onBluetoothSettings = {},
                    onManageDevices = {},
                    onSettings = {},
                    onUpgrade = {},
                    onToggleUnmatched = {},
                )
            }
        }

        composeRule.onAllNodesWithText(context.getString(R.string.overview_monitoring_off_label))
            .assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.overview_monitoring_active_label))
            .assertCountEquals(0)
    }

    @Test
    fun `the setup action invokes the callback`() {
        var clicked = false

        composeRule.setContent {
            PreviewWrapper {
                OverviewScreen(
                    state = state(effectiveMode = MonitorMode.MANUAL, address = null),
                    onRequestPermission = {},
                    onBluetoothSettings = {},
                    onManageDevices = {},
                    onSettings = {},
                    onUpgrade = {},
                    onToggleUnmatched = {},
                    onSetupPairedDevice = { clicked = true },
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.overview_monitoring_off_action))
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.runOnIdle {
            assertTrue(clicked)
        }
    }

    @Test
    fun `AUTOMATIC mode without devices keeps the searching card`() {
        composeRule.setContent {
            PreviewWrapper {
                OverviewScreen(
                    state = state(effectiveMode = MonitorMode.AUTOMATIC, address = "AA:BB:CC:DD:EE:FF"),
                    onRequestPermission = {},
                    onBluetoothSettings = {},
                    onManageDevices = {},
                    onSettings = {},
                    onUpgrade = {},
                    onToggleUnmatched = {},
                )
            }
        }

        composeRule.onAllNodesWithText(context.getString(R.string.overview_monitoring_active_label))
            .assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.overview_monitoring_off_label))
            .assertCountEquals(0)
    }
}
