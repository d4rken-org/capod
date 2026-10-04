package eu.darken.capod.main.ui.devicesettings.cards

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import eu.darken.capod.R
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.main.ui.devicesettings.previewFullState
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.profiles.core.ReactionConfig
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

class BatteryCardCaseLowTest : BaseComposeRobolectricTest() {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun render(hasCase: Boolean, notifyWhenCaseLow: Boolean) {
        val device = previewFullState(isPro = true).device!!
            .copy(reactions = ReactionConfig(notifyWhenCaseLow = notifyWhenCaseLow))
        composeRule.setContent {
            PreviewWrapper {
                BatteryCard(
                    device = device,
                    features = PodModel.Features(hasCase = hasCase),
                    isPro = true,
                    chargeCapControlEnabled = true,
                    estimateEnabled = true,
                )
            }
        }
    }

    private fun countOf(textRes: Int) = composeRule.onAllNodesWithText(context.getString(textRes))

    @Test
    fun `models with a case show the reminder toggle`() {
        render(hasCase = true, notifyWhenCaseLow = false)

        countOf(R.string.settings_caselow_reminder_label).assertCountEquals(1)
        countOf(R.string.settings_caselow_threshold_label).assertCountEquals(0)
    }

    @Test
    fun `models without a case hide the reminder toggle`() {
        render(hasCase = false, notifyWhenCaseLow = true)

        countOf(R.string.settings_caselow_reminder_label).assertCountEquals(0)
        countOf(R.string.settings_caselow_threshold_label).assertCountEquals(0)
    }

    @Test
    fun `an enabled reminder shows the threshold slider`() {
        render(hasCase = true, notifyWhenCaseLow = true)

        countOf(R.string.settings_caselow_reminder_label).assertCountEquals(1)
        countOf(R.string.settings_caselow_threshold_label).assertCountEquals(1)
    }
}
