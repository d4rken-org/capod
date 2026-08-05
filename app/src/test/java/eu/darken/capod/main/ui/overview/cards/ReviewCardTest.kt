package eu.darken.capod.main.ui.overview.cards

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import eu.darken.capod.R
import eu.darken.capod.common.compose.PreviewWrapper
import org.junit.Assert.assertTrue
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

class ReviewCardTest : BaseComposeRobolectricTest() {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val bodyText get() = context.getString(R.string.review_app_body)
    private val reviewLabel get() = context.getString(R.string.review_app_review_action)
    private val dismissLabel get() = context.getString(R.string.review_app_dismiss_action)

    // The card's title reuses the review label, so the action is matched by its click semantics.
    private val reviewButton get() = hasText(reviewLabel) and hasClickAction()

    @Test
    fun `renders the body and both actions`() {
        composeRule.setContent {
            PreviewWrapper {
                ReviewCard(onReview = {}, onDismiss = {})
            }
        }

        composeRule.onNodeWithText(bodyText).assertExists()
        composeRule.onNodeWithText(dismissLabel).assertExists()
        composeRule.onNode(reviewButton).assertIsEnabled()
    }

    @Test
    fun `both actions invoke their callback`() {
        var reviewed = false
        var dismissed = false

        composeRule.setContent {
            PreviewWrapper {
                ReviewCard(
                    onReview = { reviewed = true },
                    onDismiss = { dismissed = true },
                )
            }
        }

        composeRule.onNodeWithText(dismissLabel).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNode(reviewButton).performSemanticsAction(SemanticsActions.OnClick)

        composeRule.runOnIdle {
            assertTrue(dismissed)
            assertTrue(reviewed)
        }
    }

    @Test
    fun `the review action is disabled without a hosting activity`() {
        composeRule.setContent {
            PreviewWrapper {
                // Null callback = no Activity to launch Play's review flow with.
                ReviewCard(onReview = null, onDismiss = {})
            }
        }

        composeRule.onNode(reviewButton).assertIsNotEnabled()
        // Dismissing has to stay possible, it doesn't need an Activity.
        composeRule.onNodeWithText(dismissLabel).assertIsEnabled()
    }
}
