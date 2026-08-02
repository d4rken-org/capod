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
import eu.darken.capod.main.ui.overview.cards.components.MissingPairedDeviceBanner
import org.junit.Assert.assertTrue
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

class MissingPairedDeviceBannerTest : BaseComposeRobolectricTest() {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `states the missing paired device and what it costs`() {
        composeRule.setContent {
            PreviewWrapper {
                MissingPairedDeviceBanner(onClick = {})
            }
        }

        composeRule.onAllNodesWithText(context.getString(R.string.overview_card_missing_paired_device))
            .assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.overview_card_missing_paired_device_consequence))
            .assertCountEquals(1)
    }

    @Test
    fun `tapping the banner invokes the callback`() {
        var clicked = false

        composeRule.setContent {
            PreviewWrapper {
                MissingPairedDeviceBanner(onClick = { clicked = true })
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.overview_card_missing_paired_device))
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.runOnIdle {
            assertTrue(clicked)
        }
    }
}
