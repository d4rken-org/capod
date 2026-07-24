package eu.darken.capod.upgrade.ui

import android.app.Application
import androidx.compose.runtime.mutableStateOf
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

// Behavioral Compose tests for the offercard upgrade screen — offer visibility, enabled states,
// grace stages, restore surfaces and dialogs. Runs on the JVM via Robolectric (vintage engine
// under JUnit5).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UpgradeScreenComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    // Mirrors toLoadedState's gating (incl. verification) so enabled-state assertions match the VM.
    private fun loaded(
        ownership: Ownership = Ownership(),
        grace: GraceHint? = null,
        showRestoreBanner: Boolean = false,
        settled: Boolean = true,
        restoreInProgress: Boolean = false,
        verificationInProgress: Boolean = false,
        subscriptionAction: SubscriptionAction = SubscriptionAction.TRIAL,
        subscriptionPrice: String? = "€3.49",
        iapPrice: String? = "€6.49",
    ) = UpgradeUiState.Loaded(
        subscriptionAction = subscriptionAction,
        subscriptionEnabled = settled && ownership.subscription == null && !restoreInProgress && !verificationInProgress,
        subscriptionPrice = subscriptionPrice,
        iapEnabled = settled && !ownership.hasIap && !restoreInProgress && !verificationInProgress,
        iapPrice = iapPrice,
        ownership = ownership,
        grace = grace,
        showRestoreBanner = showRestoreBanner,
        settled = settled,
        restoreInProgress = restoreInProgress,
        verificationInProgress = verificationInProgress,
    )

    private fun setScreen(
        state: UpgradeUiState,
        onSubscription: () -> Unit = {},
        onSubscriptionTrial: () -> Unit = {},
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
                onSubscriptionTrial = onSubscriptionTrial,
                onIap = onIap,
                onRestore = onRestore,
                onManageSubscription = onManageSubscription,
                onRetry = onRetry,
            )
        }
    }

    // --- No-offers fallback ---

    private fun noOffers(skuQueryInProgress: Boolean = false, settled: Boolean = true) = loaded(
        subscriptionAction = SubscriptionAction.UNAVAILABLE,
        subscriptionPrice = null,
        iapPrice = null,
        settled = settled,
    ).copy(skuQueryInProgress = skuQueryInProgress)

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
    fun `the no-offers fallback purchase button fires onIap`() {
        var iapTapped = false
        setScreen(state = noOffers(), onIap = { iapTapped = true })

        composeRule.onNodeWithTag(UpgradeScreenTags.IAP_BUTTON)
            .performScrollTo()
            .assertIsEnabled()
            .performClick()

        iapTapped shouldBe true
    }

    @Test
    fun `no offers while a query is running shows the settling spinner, not the unavailable card`() {
        // The red "unavailable" card must not appear while offers are still being fetched.
        setScreen(state = noOffers(skuQueryInProgress = true))

        composeRule.onNodeWithTag(UpgradeScreenTags.OFFERS_SETTLING).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(UpgradeScreenTags.OFFERS_UNAVAILABLE).assertDoesNotExist()
        composeRule.onNodeWithTag(UpgradeScreenTags.RETRY_BUTTON).assertDoesNotExist()
    }

    @Test
    fun `no offers before billing has settled shows the settling spinner, not the unavailable card`() {
        // On entry the account looks like a non-owner until Play answers; the red card must wait
        // until we're actually sure Play returned nothing.
        setScreen(state = noOffers(settled = false))

        composeRule.onNodeWithTag(UpgradeScreenTags.OFFERS_SETTLING).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(UpgradeScreenTags.OFFERS_UNAVAILABLE).assertDoesNotExist()
    }

    // --- Partial offer availability ---

    @Test
    fun `subscription-only offers hide the IAP row`() {
        setScreen(loaded(iapPrice = null))

        composeRule.onNodeWithTag(UpgradeScreenTags.SUB_BUTTON).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(UpgradeScreenTags.IAP_BUTTON).assertDoesNotExist()
        composeRule.onNodeWithTag(UpgradeScreenTags.OFFERS_UNAVAILABLE).assertDoesNotExist()
    }

    @Test
    fun `iap-only offers hide the subscription row`() {
        setScreen(loaded(subscriptionAction = SubscriptionAction.UNAVAILABLE, subscriptionPrice = null))

        composeRule.onNodeWithTag(UpgradeScreenTags.IAP_BUTTON).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(UpgradeScreenTags.SUB_BUTTON).assertDoesNotExist()
        composeRule.onNodeWithTag(UpgradeScreenTags.OFFERS_UNAVAILABLE).assertDoesNotExist()
    }

    @Test
    fun `no offers at all shows the unavailable card`() {
        setScreen(noOffers())

        composeRule.onNodeWithTag(UpgradeScreenTags.OFFERS_UNAVAILABLE).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(UpgradeScreenTags.OFFERS).assertDoesNotExist()
    }

    // --- Offer routing ---

    @Test
    fun `the trial subscription routes to the trial callback`() {
        var trial = 0
        var standard = 0
        setScreen(
            loaded(subscriptionAction = SubscriptionAction.TRIAL),
            onSubscription = { standard++ },
            onSubscriptionTrial = { trial++ },
        )

        composeRule.onNodeWithTag(UpgradeScreenTags.SUB_BUTTON).performScrollTo().performClick()

        trial shouldBe 1
        standard shouldBe 0
    }

    @Test
    fun `the standard subscription routes to the standard callback`() {
        var trial = 0
        var standard = 0
        setScreen(
            loaded(subscriptionAction = SubscriptionAction.STANDARD),
            onSubscription = { standard++ },
            onSubscriptionTrial = { trial++ },
        )

        composeRule.onNodeWithTag(UpgradeScreenTags.SUB_BUTTON).performScrollTo().performClick()

        standard shouldBe 1
        trial shouldBe 0
    }

    // --- Owner states ---

    @Test
    fun `owner with renewing sub sees status and a locked switch`() {
        setScreen(loaded(ownership = Ownership(subscription = SubscriptionOwnership(isAutoRenewing = true))))

        composeRule.onNodeWithTag(UpgradeScreenTags.OWNER_HERO).assertIsDisplayed()
        composeRule.onNodeWithTag(UpgradeScreenTags.OWNER_SUB_CARD).assertIsDisplayed()
        composeRule.onNodeWithTag(UpgradeScreenTags.SWITCH_BUTTON).performScrollTo().assertIsNotEnabled()
        // No sales pitch, no acquisition offers for owners.
        composeRule.onNodeWithTag(UpgradeScreenTags.BENEFITS).assertDoesNotExist()
        composeRule.onNodeWithTag(UpgradeScreenTags.OFFERS).assertDoesNotExist()
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
    fun `the non-renewing switch stays enabled even when the IAP price is missing`() {
        setScreen(
            loaded(
                ownership = Ownership(subscription = SubscriptionOwnership(isAutoRenewing = false)),
                iapPrice = null,
            ),
        )

        composeRule.onNodeWithTag(UpgradeScreenTags.SWITCH_BUTTON).performScrollTo().assertIsEnabled()
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
                settled = false,
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

    @Test
    fun `verification in progress disables the owner restore`() {
        setScreen(
            loaded(
                ownership = Ownership(subscription = SubscriptionOwnership(isAutoRenewing = true)),
                verificationInProgress = true,
            ),
        )

        composeRule.onNodeWithTag(UpgradeScreenTags.RESTORE_BUTTON).performScrollTo().assertIsNotEnabled()
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
        // No pitch next to the grace card.
        composeRule.onNodeWithTag(UpgradeScreenTags.BENEFITS).assertDoesNotExist()

        composeRule.onNodeWithTag(UpgradeScreenTags.GRACE_RESTORE_BUTTON).performScrollTo().performClick()
        restoreTapped shouldBe true
    }

    @Test
    fun `verification disables the grace restore`() {
        setScreen(loaded(grace = GraceHint(showDiagnostics = true), verificationInProgress = true))

        composeRule.onNodeWithTag(UpgradeScreenTags.GRACE_RESTORE_BUTTON).performScrollTo().assertIsNotEnabled()
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
    fun `same-phase state changes recompose the offers in place`() {
        // Guards the AnimatedContent keying: a Loaded→Loaded change that keeps offers available
        // must update the existing offer buttons, not re-run an enter transition or get stuck on a
        // stale snapshot.
        val state = mutableStateOf(loaded(settled = false))
        composeRule.setContent {
            UpgradeScreen(
                state = state.value,
                onNavigateUp = {},
                onSubscription = {},
                onSubscriptionTrial = {},
                onIap = {},
                onRestore = {},
                onManageSubscription = {},
                onRetry = {},
            )
        }

        composeRule.onNodeWithTag(UpgradeScreenTags.SUB_BUTTON).performScrollTo().assertIsNotEnabled()

        state.value = loaded(settled = true)

        composeRule.onNodeWithTag(UpgradeScreenTags.SUB_BUTTON).performScrollTo().assertIsEnabled()
    }

    @Test
    fun `purchase buttons are disabled before billing has settled`() {
        setScreen(loaded(settled = false))

        composeRule.onNodeWithTag(UpgradeScreenTags.SUB_BUTTON).performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag(UpgradeScreenTags.IAP_BUTTON).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `returning buyers get exactly one emphasized restore action`() {
        var restored = false
        setScreen(loaded(showRestoreBanner = true), onRestore = { restored = true })

        composeRule.onNodeWithTag(UpgradeScreenTags.RESTORE_BANNER).performScrollTo().assertIsDisplayed()
        // The emphasized banner action is the ONLY restore affordance — no ordinary section below.
        composeRule.onNodeWithTag(UpgradeScreenTags.RESTORE_BUTTON).assertDoesNotExist()

        composeRule.onNodeWithTag(UpgradeScreenTags.RESTORE_BANNER_ACTION).performScrollTo().performClick()
        restored shouldBe true
    }

    @Test
    fun `verification disables the emphasized returning-buyer restore`() {
        setScreen(loaded(showRestoreBanner = true, verificationInProgress = true))

        composeRule.onNodeWithTag(UpgradeScreenTags.RESTORE_BANNER_ACTION).performScrollTo().assertIsNotEnabled()
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
    fun `restore-failed dialog contact support fires only its own callback`() {
        var contacted = 0
        var dismissed = 0
        composeRule.setContent {
            RestoreFailedDialog(onDismiss = { dismissed++ }, onContactSupport = { contacted++ })
        }

        composeRule.onNodeWithTag(UpgradeScreenTags.DIALOG_RESTORE_FAILED).assertIsDisplayed()
        composeRule.onNodeWithTag(UpgradeScreenTags.CONTACT_SUPPORT_BUTTON).performClick()

        contacted shouldBe 1
        dismissed shouldBe 0
    }
}
