package eu.darken.capod.main.ui.overview.cards

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import eu.darken.capod.R
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.common.compose.preview.MOCK_NOW
import eu.darken.capod.common.compose.preview.MockPodDataProvider
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.PodModel
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

/**
 * The mock case battery resolves at decile granularity, so a 4.0-charge spec (AirPods Pro 2) puts
 * 30% wholly above a full charge, 10% wholly below it, and 20% astride it.
 */
class CaseChargesLineTest : BaseComposeRobolectricTest() {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun charges(count: Int): String =
        context.resources.getQuantityString(R.plurals.battery_case_charges_approx, count, count)

    private fun device(case: Float, model: PodModel = PodModel.AIRPODS_PRO2): PodDevice =
        MockPodDataProvider.dualPodBatteries(case = case, model = model)

    private fun setCard(
        device: PodDevice,
        wrapper: @Composable (@Composable () -> Unit) -> Unit = { it() },
    ) {
        composeRule.setContent {
            PreviewWrapper {
                wrapper {
                    DualPodsCard(
                        device = device,
                        showDebug = false,
                        now = MOCK_NOW,
                    )
                }
            }
        }
    }

    private fun assertAdequacy(line: String, expected: Int) {
        composeRule.onNodeWithText(line).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, context.getString(expected))
        )
    }

    @Test
    fun `a model with published case figures gets a line`() {
        setCard(device(case = 0.60f))

        // 4.0 x 0.60 = 2.4
        composeRule.onNodeWithText(charges(2)).assertIsDisplayed()
    }

    @Test
    fun `a model without published case figures gets no line`() {
        setCard(device(case = 0.60f, model = PodModel.POWERBEATS_PRO))

        composeRule.onAllNodesWithText(charges(2)).assertCountEquals(0)
        composeRule.onAllNodesWithText(charges(1)).assertCountEquals(0)
        composeRule.onAllNodesWithText(context.getString(R.string.battery_case_charges_less_than_one))
            .assertCountEquals(0)
    }

    @Test
    fun `a full charge in hand reads as enough`() {
        setCard(device(case = 0.30f))

        assertAdequacy(charges(1), R.string.battery_case_charges_state_enough_cd)
    }

    @Test
    fun `a reading astride a full charge claims nothing`() {
        setCard(device(case = 0.20f))

        assertAdequacy(
            context.getString(R.string.battery_case_charges_less_than_one),
            R.string.battery_case_charges_state_uncertain_cd,
        )
    }

    @Test
    fun `a reading below a full charge reads as not enough`() {
        setCard(device(case = 0.10f))

        assertAdequacy(
            context.getString(R.string.battery_case_charges_less_than_one),
            R.string.battery_case_charges_state_not_enough_cd,
        )
    }

    @Test
    fun `an empty case says so instead of counting`() {
        setCard(device(case = 0f))

        composeRule.onNodeWithText(context.getString(R.string.battery_case_charges_empty)).assertIsDisplayed()
        composeRule.onAllNodesWithText(context.getString(R.string.battery_case_charges_less_than_one))
            .assertCountEquals(0)
    }

    @Test
    fun `the line survives a right-to-left layout`() {
        setCard(device(case = 0.60f)) { card ->
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { card() }
        }

        composeRule.onNodeWithText(charges(2)).assertIsDisplayed()
        composeRule.onNodeWithText("60%").assertIsDisplayed()
    }

    @Test
    fun `the line survives a narrow card`() {
        setCard(device(case = 0.60f)) { card ->
            Box(modifier = Modifier.width(240.dp)) { card() }
        }

        composeRule.onNodeWithText(charges(2)).assertIsDisplayed()
        composeRule.onNodeWithText("60%").assertIsDisplayed()
    }

    @Test
    fun `the line survives a large font scale`() {
        setCard(device(case = 0.60f)) { card ->
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) { card() }
        }

        composeRule.onNodeWithText(charges(2)).assertIsDisplayed()
        composeRule.onNodeWithText("60%").assertIsDisplayed()
    }
}
