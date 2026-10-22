package eu.darken.capod.main.ui.devicesettings.cards

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import eu.darken.capod.R
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.main.ui.devicesettings.previewFullState
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import io.kotest.matchers.shouldBe
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

class SoundCardTest : BaseComposeRobolectricTest() {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val equalizerLabel get() = context.getString(R.string.device_settings_equalizer_label)
    private val upgradeBadgeLabel get() = context.getString(R.string.upgrade_badge_label)

    private var equalizerClicks = 0
    private var upgradeClicks = 0

    private fun setContent(isPro: Boolean) {
        val device = previewFullState(
            isPro = isPro,
            customEq = AapSetting.CustomEq(enabled = true, low = 62, mid = 50, high = 42),
        ).device!!
        composeRule.setContent {
            PreviewWrapper {
                SoundCard(
                    device = device,
                    features = device.model.features,
                    isPro = isPro,
                    enabled = true,
                    onEqualizerClick = { equalizerClicks++ },
                    onUpgrade = { upgradeClicks++ },
                )
            }
        }
    }

    @Test
    fun `a free user gets the upgrade badge and the upgrade flow instead of the equalizer`() {
        setContent(isPro = false)

        val row = hasText(equalizerLabel) and hasText(upgradeBadgeLabel) and hasClickAction()
        composeRule.onNode(row).performSemanticsAction(SemanticsActions.OnClick)

        upgradeClicks shouldBe 1
        equalizerClicks shouldBe 0
    }

    @Test
    fun `a pro user opens the equalizer screen`() {
        setContent(isPro = true)

        val row = hasText(equalizerLabel) and hasClickAction()
        composeRule.onNode(row).performSemanticsAction(SemanticsActions.OnClick)

        equalizerClicks shouldBe 1
        upgradeClicks shouldBe 0
    }
}
