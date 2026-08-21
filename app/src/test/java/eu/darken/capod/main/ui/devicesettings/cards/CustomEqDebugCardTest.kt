package eu.darken.capod.main.ui.devicesettings.cards

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.aap.AapPodState
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import io.kotest.matchers.shouldBe
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

/**
 * AapOutboundController queues every ear-gated command while no pod is worn, and the settings
 * coordinator collapses repeated ones to the latest tuple, so a tap made with the pods out would
 * emit its packet at some unrelated later moment. That destroys the logcat observation window this
 * debug card exists to produce, hence Apply has to be unavailable while a write would be queued.
 *
 * The controller only gates when the AAP EarDetection setting is present, so with no such report
 * the write goes out immediately and Apply must stay available.
 */
class CustomEqDebugCardTest : BaseComposeRobolectricTest() {

    private val applyButton = hasText("Apply") and hasClickAction()

    private var applies = 0

    private fun device(earDetection: AapSetting.EarDetection?) = PodDevice(
        profileId = "test",
        ble = null,
        aap = AapPodState(
            connectionState = AapPodState.ConnectionState.READY,
            settings = earDetection?.let { mapOf(AapSetting.EarDetection::class to it) } ?: emptyMap(),
        ),
    )

    private fun setContent(earDetection: AapSetting.EarDetection?) {
        composeRule.setContent {
            PreviewWrapper {
                // Scrollable, because the card is taller than the test window and the Apply row
                // sits at its bottom. Without a scroll the taps would land off-screen and every
                // "no command was sent" assertion would pass for the wrong reason.
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    CustomEqDebugCard(
                        device = device(earDetection),
                        enabled = true,
                        onApply = { _, _, _, _ -> applies++ },
                    )
                }
            }
        }
    }

    private fun clickApply() {
        composeRule.onNode(applyButton).performScrollTo().performClick()
    }

    @Test
    fun `apply is available while a pod is in ear`() {
        setContent(
            AapSetting.EarDetection(
                primaryPod = AapSetting.EarDetection.PodPlacement.IN_EAR,
                secondaryPod = AapSetting.EarDetection.PodPlacement.NOT_IN_EAR,
            ),
        )

        composeRule.onNode(applyButton).assertIsEnabled()
        composeRule.onNodeWithText(NOT_IN_EAR_NOTICE).assertDoesNotExist()

        clickApply()
        composeRule.runOnIdle { applies shouldBe 1 }
    }

    @Test
    fun `apply is unavailable with both pods out, with a notice next to the button`() {
        setContent(
            AapSetting.EarDetection(
                primaryPod = AapSetting.EarDetection.PodPlacement.NOT_IN_EAR,
                secondaryPod = AapSetting.EarDetection.PodPlacement.IN_CASE,
            ),
        )

        composeRule.onNode(applyButton).assertIsNotEnabled()
        composeRule.onNodeWithText(NOT_IN_EAR_NOTICE).assertExists()

        clickApply()
        composeRule.runOnIdle { applies shouldBe 0 }
    }

    @Test
    fun `apply is available while no ear detection is reported at all`() {
        // No 0x06 report yet, so AapOutboundController's ear gate does not engage and the write
        // is sent right away. Blocking here would be both pointless and factually wrong.
        setContent(null)

        composeRule.onNode(applyButton).assertIsEnabled()
        composeRule.onNodeWithText(NOT_IN_EAR_NOTICE).assertDoesNotExist()

        clickApply()
        composeRule.runOnIdle { applies shouldBe 1 }
    }
}
