package eu.darken.capod.main.ui.overview.cards

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import eu.darken.capod.R
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.common.compose.preview.MOCK_NOW
import eu.darken.capod.common.compose.preview.MockPodDataProvider
import eu.darken.capod.monitor.core.PodDevice
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

/**
 * Every slot on the overview reports its level as a state description, which is both the screen
 * reader's only access to the threshold and the observable half of the colour: the tier a slot
 * announces here is the same tier it hands to the colour mapper covered by BatteryColorsThemeTest.
 */
class BatteryLevelStateTest : BaseComposeRobolectricTest() {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val low: String
        get() = context.getString(R.string.battery_state_low_cd)

    private val critical: String
        get() = context.getString(R.string.battery_state_critical_cd)

    /** Left pod low, right pod critical, case low — three distinct percentages, three tiers. */
    private fun mixedDualPods(): PodDevice = MockPodDataProvider.dualPodBatteries(
        left = 0.22f,
        right = 0.05f,
        case = 0.20f,
    )

    private fun setDualPods(device: PodDevice, collapsed: Boolean) {
        composeRule.setContent {
            PreviewWrapper {
                DualPodsCard(
                    device = device,
                    showDebug = false,
                    now = MOCK_NOW,
                    isCollapsed = collapsed,
                    onToggleCollapse = {},
                )
            }
        }
    }

    private fun assertState(text: String, expected: String) {
        composeRule.onNodeWithText(text)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, expected))
    }

    @Test
    fun `the expanded pod gauges report their level`() {
        setDualPods(mixedDualPods(), collapsed = false)

        assertState("22%", low)
        assertState("5%", critical)
    }

    @Test
    fun `the expanded case row reports its level`() {
        setDualPods(mixedDualPods(), collapsed = false)

        assertState("20%", low)
    }

    @Test
    fun `the collapsed pod rings report their level`() {
        setDualPods(mixedDualPods(), collapsed = true)

        assertState("22%", low)
        assertState("5%", critical)
    }

    @Test
    fun `the collapsed case cluster reports its level`() {
        setDualPods(mixedDualPods(), collapsed = true)

        assertState("20%", low)
    }

    @Test
    fun `the single pod gauge reports a low level`() {
        composeRule.setContent {
            PreviewWrapper {
                SinglePodsCard(
                    device = MockPodDataProvider.singlePodBattery(percent = 0.20f),
                    showDebug = false,
                    now = MOCK_NOW,
                )
            }
        }

        assertState("20%", low)
    }

    @Test
    fun `the single pod gauge reports a critical level`() {
        composeRule.setContent {
            PreviewWrapper {
                SinglePodsCard(
                    device = MockPodDataProvider.singlePodBattery(percent = 0.08f),
                    showDebug = false,
                    now = MOCK_NOW,
                )
            }
        }

        assertState("8%", critical)
    }

    @Test
    fun `a healthy level makes no claim`() {
        setDualPods(MockPodDataProvider.dualPodBatteries(left = 0.80f, right = 0.45f, case = 0.60f), collapsed = false)

        composeRule.onNodeWithText("80%")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription).not())
        composeRule.onNodeWithText("60%")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription).not())
    }
}
