package eu.darken.capod.upgrade.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UpgradeScreenFossComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `supporter status shows the supporter card`() {
        composeRule.setContent {
            SupporterStatusScreen(
                upgradedAt = Instant.parse("2025-11-02T12:00:00Z"),
                onNavigateUp = {},
                onSponsorPage = {},
            )
        }

        composeRule.onNodeWithTag("upgrade.foss.supporterCard").assertIsDisplayed()
    }

    @Test
    fun `supporter sponsor link fires the plain callback`() {
        var sponsorTapped = false
        composeRule.setContent {
            SupporterStatusScreen(
                upgradedAt = Instant.parse("2025-11-02T12:00:00Z"),
                onNavigateUp = {},
                onSponsorPage = { sponsorTapped = true },
            )
        }

        val label = androidx.test.core.app.ApplicationProvider
            .getApplicationContext<android.content.Context>()
            .getString(eu.darken.capod.R.string.upgrade_foss_sponsor_again_action)
        composeRule.onNode(androidx.compose.ui.test.hasText(label)).performScrollTo().performClick()

        sponsorTapped shouldBe true
    }
}
