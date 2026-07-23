package eu.darken.capod.upgrade.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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

// Behavioral Compose tests for the upgrade screen states — offer visibility, enabled states,
// grace stages and dialogs. Runs on the JVM via Robolectric (vintage engine under JUnit5).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UpgradeScreenComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun loaded(
        ownership: Ownership = Ownership(),
        grace: GraceHint? = null,
        showRestoreBanner: Boolean = false,
        settledEnabled: Boolean = true,
        restoreInProgress: Boolean = false,
        verificationInProgress: Boolean = false,
    ) = UpgradeUiState.Loaded(
        subscriptionAction = SubscriptionAction.TRIAL,
        subscriptionEnabled = settledEnabled && ownership.subscription == null && !restoreInProgress,
        subscriptionPrice = "€3.49",
        iapEnabled = settledEnabled && !ownership.hasIap && !restoreInProgress,
        iapPrice = "€6.49",
        ownership = ownership,
        grace = grace,
        showRestoreBanner = showRestoreBanner,
        settled = settledEnabled,
        restoreInProgress = restoreInProgress,
        verificationInProgress = verificationInProgress,
    )

    private fun setScreen(
        state: UpgradeUiState,
        onSubscription: () -> Unit = {},
        onIap: () -> Unit = {},
        onRestore: () -> Unit = {},
        onManageSubscription: () -> Unit = {},
        onRetry: () -> Unit = {},
    ) {
        composeRule.setContent {
            UpgradeScreen(
                state = state,
                onNavigateUp = {},
                onSubscription = onSubscription,
                onSubscriptionTrial = onSubscription,
                onIap = onIap,
                onRestore = onRestore,
                onManageSubscription = onManageSubscription,
                onRetry = onRetry,
            )
        }
    }

    // No-offer fallback state (cold/slow Play store returned no product details).
    private fun noOffers(skuQueryInProgress: Boolean = false) = UpgradeUiState.Loaded(
        subscriptionAction = SubscriptionAction.UNAVAILABLE,
        subscriptionEnabled = false,
        subscriptionPrice = null,
        iapEnabled = true,
        iapPrice = null,
        skuQueryInProgress = skuQueryInProgress,
    )

    @Test
    fun `the no-offers fallback shows a Retry that fires the callback`() {
        var retries = 0
        setScreen(state = noOffers(), onRetry = { retries++ })

        composeRule.onNodeWithTag(UpgradeScreenTags.RETRY_BUTTON)
            .performScrollTo()
            .assertIsEnabled()
            .performClick()

        retries shouldBe 1
    }

    @Test
    fun `the Retry button is disabled while a SKU query is running`() {
        setScreen(state = noOffers(skuQueryInProgress = true))

        composeRule.onNodeWithTag(UpgradeScreenTags.RETRY_BUTTON)
            .performScrollTo()
            .assertIsNotEnabled()
    }

    // --- Owner states ---

    @Test
    fun `owner with renewing sub sees status and a locked switch`() {
        setScreen(loaded(ownership = Ownership(subscription = SubscriptionOwnership(isAutoRenewing = true))))

        composeRule.onNodeWithTag(UpgradeScreenTags.OWNER_HERO).assertIsDisplayed()
        composeRule.onNodeWithTag(UpgradeScreenTags.OWNER_SUB_CARD).assertIsDisplayed()
        composeRule.onNodeWithTag(UpgradeScreenTags.SWITCH_BUTTON).performScrollTo().assertIsNotEnabled()
        // No sales pitch, no acquisition buttons for owners.
        composeRule.onNodeWithTag(UpgradeScreenTags.BENEFITS).assertDoesNotExist()
        composeRule.onNodeWithTag(UpgradeScreenTags.SUB_BUTTON).assertDoesNotExist()
    }

    @Test
    fun `owner with non-renewing sub can use the switch`() {
        var iapTapped = false
        setScreen(
            loaded(ownership = Ownership(subscription = SubscriptionOwnership(isAutoRenewing = false))),
            onIap = { iapTapped = true },
        )

        val button = composeRule.onNodeWithTag(UpgradeScreenTags.SWITCH_BUTTON).performScrollTo()
        button.assertIsEnabled()
        button.performClick()

        iapTapped shouldBe true
    }

    @Test
    fun `iap owner sees the ownership card but no switch or manage actions`() {
        setScreen(loaded(ownership = Ownership(hasIap = true)))

        composeRule.onNodeWithTag(UpgradeScreenTags.OWNER_HERO).assertIsDisplayed()
        composeRule.onNodeWithTag(UpgradeScreenTags.OWNER_IAP_CARD).assertIsDisplayed()
        composeRule.onNodeWithTag(UpgradeScreenTags.SWITCH_CARD).assertDoesNotExist()
        composeRule.onNodeWithTag(UpgradeScreenTags.MANAGE_SUB_BUTTON).assertDoesNotExist()
        composeRule.onNodeWithTag(UpgradeScreenTags.OWNER_WARNING).assertDoesNotExist()
    }

    @Test
    fun `owning both while the sub still renews shows the double-payment warning`() {
        setScreen(
            loaded(
                ownership = Ownership(
                    hasIap = true,
                    subscription = SubscriptionOwnership(isAutoRenewing = true),
                ),
            ),
        )

        composeRule.onNodeWithTag(UpgradeScreenTags.OWNER_WARNING).performScrollTo().assertIsDisplayed()
        // Both products owned -> no switch offer.
        composeRule.onNodeWithTag(UpgradeScreenTags.SWITCH_CARD).assertDoesNotExist()
    }

    @Test
    fun `manage subscription fires the callback`() {
        var managed = false
        setScreen(
            loaded(ownership = Ownership(subscription = SubscriptionOwnership(isAutoRenewing = true))),
            onManageSubscription = { managed = true },
        )

        composeRule.onNodeWithTag(UpgradeScreenTags.MANAGE_SUB_BUTTON).performScrollTo().performClick()

        managed shouldBe true
    }

    @Test
    fun `the switch honors the settled gate`() {
        setScreen(
            loaded(
                ownership = Ownership(subscription = SubscriptionOwnership(isAutoRenewing = false)),
                settledEnabled = false,
            ),
        )

        composeRule.onNodeWithTag(UpgradeScreenTags.SWITCH_BUTTON).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `verification in progress disables the unlocked switch`() {
        setScreen(
            loaded(
                ownership = Ownership(subscription = SubscriptionOwnership(isAutoRenewing = false)),
                verificationInProgress = true,
            ),
        )

        composeRule.onNodeWithTag(UpgradeScreenTags.SWITCH_BUTTON).performScrollTo().assertIsNotEnabled()
    }

    // --- Grace states ---

    @Test
    fun `quiet grace stage hides offers and restore`() {
        setScreen(loaded(grace = GraceHint(showDiagnostics = false)))

        composeRule.onNodeWithTag(UpgradeScreenTags.GRACE_CARD).assertIsDisplayed()
        composeRule.onNodeWithTag(UpgradeScreenTags.GRACE_RESTORE_BUTTON).assertDoesNotExist()
        composeRule.onNodeWithTag(UpgradeScreenTags.SUB_BUTTON).assertDoesNotExist()
        composeRule.onNodeWithTag(UpgradeScreenTags.IAP_BUTTON).assertDoesNotExist()
        composeRule.onNodeWithTag(UpgradeScreenTags.BENEFITS).assertDoesNotExist()
    }

    @Test
    fun `diagnostics grace stage shows restore and brings the offers back`() {
        var restoreTapped = false
        setScreen(
            loaded(grace = GraceHint(showDiagnostics = true)),
            onRestore = { restoreTapped = true },
        )

        composeRule.onNodeWithTag(UpgradeScreenTags.GRACE_CARD).assertIsDisplayed()
        // Offers return so an actually-expired subscriber can switch without waiting out grace.
        composeRule.onNodeWithTag(UpgradeScreenTags.SUB_BUTTON).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(UpgradeScreenTags.IAP_BUTTON).performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag(UpgradeScreenTags.GRACE_RESTORE_BUTTON).performScrollTo().performClick()
        restoreTapped shouldBe true
    }

    // --- Acquisition states ---

    @Test
    fun `acquisition shows pitch and enabled purchase buttons`() {
        setScreen(loaded())

        composeRule.onNodeWithTag(UpgradeScreenTags.BENEFITS).assertIsDisplayed()
        composeRule.onNodeWithTag(UpgradeScreenTags.SUB_BUTTON).performScrollTo().assertIsEnabled()
        composeRule.onNodeWithTag(UpgradeScreenTags.IAP_BUTTON).performScrollTo().assertIsEnabled()
        composeRule.onNodeWithTag(UpgradeScreenTags.OWNER_HERO).assertDoesNotExist()
        composeRule.onNodeWithTag(UpgradeScreenTags.GRACE_CARD).assertDoesNotExist()
    }

    @Test
    fun `purchase buttons are disabled before billing has settled`() {
        setScreen(loaded(settledEnabled = false))

        composeRule.onNodeWithTag(UpgradeScreenTags.SUB_BUTTON).performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag(UpgradeScreenTags.IAP_BUTTON).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `restore banner appears for returning buyers`() {
        setScreen(loaded(showRestoreBanner = true))

        composeRule.onNodeWithTag(UpgradeScreenTags.RESTORE_BANNER).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `restore in progress disables purchase and restore buttons`() {
        setScreen(loaded(restoreInProgress = true))

        composeRule.onNodeWithTag(UpgradeScreenTags.SUB_BUTTON).performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag(UpgradeScreenTags.IAP_BUTTON).performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag(UpgradeScreenTags.RESTORE_BUTTON).performScrollTo().assertIsNotEnabled()
    }

    // --- Dialogs ---

    @Test
    fun `still-renewing dialog offers the manage action`() {
        var managed = false
        var dismissed = false
        composeRule.setContent {
            StillRenewingDialog(onManage = { managed = true }, onDismiss = { dismissed = true })
        }

        composeRule.onNodeWithTag(UpgradeScreenTags.DIALOG_STILL_RENEWING).assertIsDisplayed()
        composeRule.onNodeWithTag(UpgradeScreenTags.MANAGE_SUB_BUTTON).performClick()

        managed shouldBe true
        dismissed shouldBe false
    }

    @Test
    fun `check-failed dialog renders`() {
        composeRule.setContent {
            CheckFailedDialog(onDismiss = {})
        }

        composeRule.onNodeWithTag(UpgradeScreenTags.DIALOG_CHECK_FAILED).assertIsDisplayed()
    }

    @Test
    fun `restore-failed dialog renders the troubleshooting hints`() {
        composeRule.setContent {
            RestoreFailedDialog(onDismiss = {})
        }

        composeRule.onNodeWithTag(UpgradeScreenTags.DIALOG_RESTORE_FAILED).assertIsDisplayed()
    }
}
