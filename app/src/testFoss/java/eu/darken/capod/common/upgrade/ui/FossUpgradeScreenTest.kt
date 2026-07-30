package eu.darken.capod.common.upgrade.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.semantics.SemanticsActions
import eu.darken.capod.R
import eu.darken.capod.common.compose.PreviewWrapper
import org.junit.Assert.assertTrue
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class FossUpgradeScreenTest : BaseComposeRobolectricTest() {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `renders redesigned foss content without duplicated app bar title`() {
        composeRule.setUpgradeContent {
            UpgradeScreen()
        }

        composeRule.onAllNodesWithText(context.getString(R.string.settings_upgrade_status_label)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_foss_preamble)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_how_title)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_how_body)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_why_title)).assertCountEquals(1)
        // capod renders the benefit list from its own per-item ids (see upgradeBenefitsText()).
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_benefit_themes)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_foss_sponsor_subtitle)).assertCountEquals(1)
        composeRule.onAllNodesWithTag(UpgradeScreenTags.FOSS_SPONSOR).assertCountEquals(1)
    }

    @Test
    fun `sponsor button invokes callback`() {
        var clicked = false

        composeRule.setUpgradeContent {
            UpgradeScreen(onGithubSponsors = { clicked = true })
        }

        composeRule.onAllNodesWithTag(UpgradeScreenTags.FOSS_SPONSOR).assertCountEquals(1)
        composeRule.onNodeWithTag(UpgradeScreenTags.FOSS_SPONSOR).performSemanticsAction(SemanticsActions.OnClick)

        composeRule.runOnIdle {
            assertTrue(clicked)
        }
    }

    @Test
    fun `free status view shows the status without any pitch content`() {
        composeRule.setUpgradeContent {
            UpgradeScreen(view = FossUpgradeView.STATUS_FREE)
        }

        // "CAPod FOSS", not "CAPod Pro": the status views describe a FOSS install.
        composeRule.onAllNodesWithText(context.getString(R.string.app_name_foss)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.app_name_pro)).assertCountEquals(0)
        composeRule.onAllNodesWithTag(UpgradeScreenTags.FOSS_STATUS_FREE).assertCountEquals(1)
        composeRule.onAllNodesWithTag(UpgradeScreenTags.FOSS_SHOW_OPTIONS).assertCountEquals(1)
        composeRule.onAllNodesWithTag(UpgradeScreenTags.FOSS_SPONSOR).assertCountEquals(0)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_foss_preamble)).assertCountEquals(0)
    }

    @Test
    fun `upgrade options button invokes callback`() {
        var clicked = false

        composeRule.setUpgradeContent {
            UpgradeScreen(view = FossUpgradeView.STATUS_FREE, onShowUpgradeOptions = { clicked = true })
        }

        composeRule.onNodeWithTag(UpgradeScreenTags.FOSS_SHOW_OPTIONS)
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.runOnIdle {
            assertTrue(clicked)
        }
    }

    @Test
    fun `upgraded status view thanks the supporter and offers a recurring donation`() {
        val since = Instant.ofEpochMilli(1_700_000_000_000L)
        composeRule.setUpgradeContent {
            UpgradeScreen(view = FossUpgradeView.STATUS_UPGRADED, supporterSince = since)
        }

        composeRule.onAllNodesWithText(context.getString(R.string.app_name_foss)).assertCountEquals(1)
        composeRule.onAllNodesWithTag(UpgradeScreenTags.FOSS_STATUS_UPGRADED).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_foss_supporter_thanks))
            .assertCountEquals(1)
        val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())
        composeRule.onAllNodesWithText(
            context.getString(R.string.upgrade_foss_supporter_since, formatter.format(since))
        ).assertCountEquals(1)
        composeRule.onAllNodesWithTag(UpgradeScreenTags.FOSS_DONATE).assertCountEquals(1)
        composeRule.onAllNodesWithTag(UpgradeScreenTags.FOSS_SHOW_OPTIONS).assertCountEquals(0)
        composeRule.onAllNodesWithTag(UpgradeScreenTags.FOSS_SPONSOR).assertCountEquals(0)
    }

    @Test
    fun `the supporter-since line stays away without a date`() {
        // UpgradeRepoFoss can report an upgrade whose record predates the timestamp: no date line
        // instead of a bogus one.
        composeRule.setUpgradeContent {
            UpgradeScreen(view = FossUpgradeView.STATUS_UPGRADED, supporterSince = null)
        }

        val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())
        composeRule.onAllNodesWithText(
            context.getString(R.string.upgrade_foss_supporter_since, formatter.format(Instant.EPOCH))
        ).assertCountEquals(0)
        composeRule.onAllNodesWithTag(UpgradeScreenTags.FOSS_STATUS_UPGRADED).assertCountEquals(1)
    }

    @Test
    fun `recurring donation button invokes the sponsors callback`() {
        var clicked = false

        composeRule.setUpgradeContent {
            UpgradeScreen(view = FossUpgradeView.STATUS_UPGRADED, onGithubSponsors = { clicked = true })
        }

        composeRule.onNodeWithTag(UpgradeScreenTags.FOSS_DONATE)
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.runOnIdle {
            assertTrue(clicked)
        }
    }
}

private fun ComposeContentTestRule.setUpgradeContent(
    content: @Composable () -> Unit,
) {
    setContent {
        PreviewWrapper {
            content()
        }
    }
}

