package eu.darken.capod.common.error

import android.app.Activity
import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.common.flow.SingleEventFlow
import io.kotest.matchers.shouldBe
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

/**
 * The shared error dialog dispatches arbitrary fix actions: one that blows up must never take the
 * UI — or the dialog's exit — down with it. Flavor-independent, so it runs on both legs.
 */
class ComposeErrorDialogGuardTest : BaseComposeRobolectricTest() {

    private class FakeErrorSource : ErrorEventSource2 {
        override val errorEvents = SingleEventFlow<Throwable>()
    }

    private class TestError(
        private val fixAction: (Activity) -> Unit,
    ) : Exception(ERROR_BODY), HasLocalizedError {
        override fun getLocalizedError(context: Context): LocalizedError = LocalizedError(
            throwable = this,
            label = ERROR_TITLE,
            description = ERROR_BODY,
            fixActionLabel = FIX_LABEL,
            fixAction = fixAction,
        )
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
    fun `a throwing fix action still dismisses the dialog`() {
        var invoked = false
        showError(
            TestError {
                // Flag first: the assertion below has to distinguish "action ran and threw" from
                // "action was never dispatched".
                invoked = true
                throw IllegalStateException("fix action exploded")
            }
        )

        composeRule.onNodeWithText(FIX_LABEL).performClick()
        composeRule.waitForIdle()

        invoked shouldBe true
        // The handler latches on the current error: the dialog only goes away if the throw was
        // caught and onDismiss still ran.
        composeRule.onAllNodesWithText(FIX_LABEL).assertCountEquals(0)
    }
}

private const val ERROR_TITLE = "Test error title"
private const val ERROR_BODY = "Test error description"
private const val FIX_LABEL = "Fix it"
