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
import io.kotest.matchers.shouldBe
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

/**
 * The card only disappears once the next state emission arrives, so its two tap targets need a
 * latch: a dismiss after a review would overwrite the completed-review bookkeeping with a snooze,
 * a review after a dismiss would re-open what the user just closed. The latch is asymmetric —
 * repeated review taps stay allowed, because a Play request can fail without persisting anything,
 * which leaves the card on screen and in need of a retry.
 */
class ReviewCardTest : BaseComposeRobolectricTest() {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val bodyText get() = context.getString(R.string.review_app_body)
    private val reviewLabel get() = context.getString(R.string.review_app_review_action)
    private val dismissLabel get() = context.getString(R.string.review_app_dismiss_action)

    // The card's title reuses the review label, so the action is matched by its click semantics.
    private val reviewButton get() = hasText(reviewLabel) and hasClickAction()

    private var reviews = 0
    private var dismisses = 0

    private fun setContent(withActivity: Boolean = true) {
        composeRule.setContent {
            PreviewWrapper {
                ReviewCard(
                    // Null callback = no Activity to launch Play's review flow with.
                    onReview = if (withActivity) ({ reviews++ }) else null,
                    onDismiss = { dismisses++ },
                )
            }
        }
    }

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

    @Test
    fun `a dismissed card ignores a later review tap`() {
        setContent()

        composeRule.onNodeWithText(dismissLabel).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle { dismisses shouldBe 1 }

        composeRule.onNode(reviewButton).assertIsNotEnabled()
        composeRule.onNode(reviewButton).performSemanticsAction(SemanticsActions.OnClick)

        composeRule.runOnIdle {
            reviews shouldBe 0
            dismisses shouldBe 1
        }
    }

    @Test
    fun `a reviewed card ignores a later dismiss tap`() {
        setContent()

        composeRule.onNode(reviewButton).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle { reviews shouldBe 1 }

        composeRule.onNodeWithText(dismissLabel).assertIsNotEnabled()
        composeRule.onNodeWithText(dismissLabel).performSemanticsAction(SemanticsActions.OnClick)

        composeRule.runOnIdle {
            reviews shouldBe 1
            dismisses shouldBe 0
        }
    }

    @Test
    fun `repeated review taps are not absorbed by the card`() {
        setContent()

        composeRule.onNode(reviewButton).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle { reviews shouldBe 1 }

        // A failed Play request persists nothing and leaves the card up, so the retry has to work.
        // Duplicates are the tool's problem, it holds a single-flight lock for exactly this.
        composeRule.onNode(reviewButton).assertIsEnabled()
        composeRule.onNode(reviewButton).performSemanticsAction(SemanticsActions.OnClick)

        composeRule.runOnIdle {
            reviews shouldBe 2
            dismisses shouldBe 0
        }
    }

    @Test
    fun `a review tap without an activity consumes neither latch`() {
        setContent(withActivity = false)

        composeRule.onNode(reviewButton).assertIsNotEnabled()
        composeRule.onNode(reviewButton).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle { reviews shouldBe 0 }

        // Nothing was handed to the caller, so the card must still be dismissable
        composeRule.onNodeWithText(dismissLabel).assertIsEnabled()
        composeRule.onNodeWithText(dismissLabel).performSemanticsAction(SemanticsActions.OnClick)

        composeRule.runOnIdle { dismisses shouldBe 1 }
    }
}
