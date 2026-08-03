package eu.darken.capod.common.error

import android.content.Context
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import eu.darken.capod.R
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.common.flow.SingleEventFlow
import eu.darken.capod.common.upgrade.core.billing.GplayServiceUnavailableException
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication

/**
 * The shared error dialog: an error that offers a way out gets its own action plus a dismiss, and
 * everything else must keep the acknowledge-only shape — [ErrorEventHandler] backs every screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class ComposeErrorDialogTest : BaseTest() {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private class FakeErrorSource : ErrorEventSource2 {
        override val errorEvents = SingleEventFlow<Throwable>()
    }

    private fun showError(error: Throwable) {
        val source = FakeErrorSource()
        composeRule.setContent {
            PreviewWrapper {
                ErrorEventHandler(source)
            }
        }
        // Buffered channel: the event survives until the handler's collector attaches.
        source.errorEvents.tryEmit(error)
        composeRule.waitForIdle()
    }

    @Test
    fun `a fixable error offers its action next to a dismiss`() {
        showError(GplayServiceUnavailableException(RuntimeException("Play hiccup")))

        composeRule.onNodeWithText(context.getString(R.string.upgrades_gplay_unavailable_error)).assertExists()
        composeRule.onNodeWithText("Google Play").assertExists()
        composeRule.onNodeWithText(context.getString(R.string.general_dismiss_action)).assertExists()
        // Nothing is being cancelled or merely acknowledged here.
        composeRule.onAllNodesWithText(context.getString(R.string.general_cancel_action)).assertCountEquals(0)
        composeRule.onAllNodesWithText(context.getString(android.R.string.ok)).assertCountEquals(0)
    }

    @Test
    fun `the fix action opens Google Play's app info and closes the dialog`() {
        showError(GplayServiceUnavailableException(RuntimeException("Play hiccup")))

        composeRule.onNodeWithText("Google Play").performClick()
        composeRule.waitForIdle()

        val started = shadowOf(composeRule.activity).nextStartedActivity.shouldNotBeNull()
        started.action shouldBe Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        started.data.toString() shouldBe "package:com.android.vending"
        // The user acted: leaving the dialog up would greet them again on the way back.
        composeRule.onAllNodesWithText("Google Play").assertCountEquals(0)
    }

    @Test
    fun `dismissing closes the dialog without launching anything`() {
        showError(GplayServiceUnavailableException(RuntimeException("Play hiccup")))

        composeRule.onNodeWithText(context.getString(R.string.general_dismiss_action)).performClick()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText(context.getString(R.string.general_dismiss_action)).assertCountEquals(0)
        shadowOf(composeRule.activity).nextStartedActivity shouldBe null
    }

    @Test
    fun `an ordinary error keeps the acknowledge-only dialog`() {
        // The handler is shared by every screen: the fix/dismiss pair must stay exclusive to errors
        // that actually carry a fix action.
        showError(RuntimeException("something went wrong"))

        composeRule.onNodeWithText(context.getString(android.R.string.ok)).assertExists()
        composeRule.onAllNodesWithText(context.getString(R.string.general_dismiss_action)).assertCountEquals(0)
        composeRule.onAllNodesWithText("Google Play").assertCountEquals(0)
    }

    @Test
    fun `the unavailable-billing error carries a fix action`() {
        val localized = GplayServiceUnavailableException(RuntimeException("Play hiccup")).getLocalizedError(context)

        localized.fixActionLabel shouldBe "Google Play"
        localized.fixAction.shouldNotBeNull()
    }
}
