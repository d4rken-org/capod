package eu.darken.capod.upgrade.ui

import android.app.Activity
import com.android.billingclient.api.Purchase
import eu.darken.capod.common.WebpageTool
import eu.darken.capod.common.navigation.NavEvent
import eu.darken.capod.common.upgrade.core.CapodSku
import eu.darken.capod.common.upgrade.core.UpgradeRepoGplay
import eu.darken.capod.common.upgrade.core.client.UserCanceledBillingException
import eu.darken.capod.common.upgrade.core.data.BillingData
import eu.darken.capod.common.upgrade.core.data.PurchasedSku
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.TestTimeSource
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2
import java.time.Duration

class UpgradeViewModelTest : BaseTest() {

    private val timeSource = TestTimeSource()

    private fun now(): Long = timeSource.currentTimeMillis()

    private fun mockPurchase(productId: String, autoRenewing: Boolean = false): Purchase = mockk {
        every { products } returns listOf(productId)
        every { purchaseTime } returns 1_000L
        every { isAutoRenewing } returns autoRenewing
    }

    private fun ownerInfo(vararg purchases: Purchase): UpgradeRepoGplay.Info {
        val purchased = purchases.map { purchase ->
            val sku = CapodSku.PRO_SKUS.first { it.id in purchase.products }
            PurchasedSku(sku, purchase)
        }
        return UpgradeRepoGplay.Info(
            billingData = BillingData(purchases.toList()),
            upgrades = purchased,
        )
    }

    private fun mockRepo(): UpgradeRepoGplay = mockk<UpgradeRepoGplay>(relaxed = true).apply {
        every { upgradeInfo } returns MutableStateFlow(UpgradeRepoGplay.Info(billingData = null))
        every { wasEverPro } returns MutableStateFlow(false)
        every { proUnconfirmedSince } returns MutableStateFlow(0L)
        every { isSettled } returns MutableStateFlow(true)
        coEvery { queryCurrentSubscriptions() } returns emptyList()
        coEvery { querySkus(any()) } returns emptyList()
    }

    private fun TestScope.createVm(
        repo: UpgradeRepoGplay,
        manage: Boolean? = false,
    ) = UpgradeViewModel(
        dispatcherProvider = TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler)),
        upgradeRepo = repo,
        webpageTool = mockk<WebpageTool>(relaxed = true),
        timeSource = timeSource,
    ).also { vm -> manage?.let { vm.initialize(it) } }

    // --- Restore semantics ---

    @Test
    fun `restore with no purchase emits RestoreFailed`() = runTest2 {
        val repo = mockRepo()
        coEvery { repo.restorePurchaseNow() } returns UpgradeRepoGplay.Info(billingData = null)
        val vm = createVm(repo)

        val event = async { vm.events.first() }
        vm.restorePurchase()
        advanceUntilIdle()

        event.await() shouldBe UpgradeViewModel.UpgradeEvent.RestoreFailed
    }

    @Test
    fun `a grace-only restore result is NOT a success`() = runTest2 {
        // isPro via grace means Play still couldn't confirm anything — the user must get the
        // troubleshooting dialog, not a success message.
        val repo = mockRepo()
        coEvery { repo.restorePurchaseNow() } returns UpgradeRepoGplay.Info(
            gracePeriod = true,
            billingData = null,
        )
        val vm = createVm(repo)

        val event = async { vm.events.first() }
        vm.restorePurchase()
        advanceUntilIdle()

        event.await() shouldBe UpgradeViewModel.UpgradeEvent.RestoreFailed
    }

    @Test
    fun `restore that finds an actual purchase emits RestoreSucceeded`() = runTest2 {
        val repo = mockRepo()
        coEvery { repo.restorePurchaseNow() } returns ownerInfo(mockPurchase(CapodSku.Iap.PRO_UPGRADE.id))
        val vm = createVm(repo)

        val event = async { vm.events.first() }
        vm.restorePurchase()
        advanceUntilIdle()

        event.await() shouldBe UpgradeViewModel.UpgradeEvent.RestoreSucceeded
    }

    @Test
    fun `restore that times out emits RestoreFailed`() = runTest2 {
        val repo = mockRepo()
        coEvery { repo.restorePurchaseNow() } coAnswers {
            delay(UpgradeViewModel.RESTORE_TIMEOUT_MS * 2)
            UpgradeRepoGplay.Info(gracePeriod = true, billingData = null)
        }
        val vm = createVm(repo)

        val event = async { vm.events.first() }
        vm.restorePurchase()
        advanceUntilIdle()

        event.await() shouldBe UpgradeViewModel.UpgradeEvent.RestoreFailed
    }

    @Test
    fun `restore that errors forwards the error instead of RestoreFailed`() = runTest2 {
        val repo = mockRepo()
        val boom = IllegalStateException("Play unavailable")
        coEvery { repo.restorePurchaseNow() } throws boom
        val vm = createVm(repo)

        val forwardedError = async { vm.errorEvents.first() }
        vm.restorePurchase()
        advanceUntilIdle()

        forwardedError.await() shouldBe boom
    }

    @Test
    fun `restore is single-flight, taps during a running restore are ignored`() = runTest2 {
        val repo = mockRepo()
        coEvery { repo.restorePurchaseNow() } coAnswers {
            delay(5_000)
            UpgradeRepoGplay.Info(gracePeriod = true, billingData = null)
        }
        val vm = createVm(repo)

        vm.restorePurchase()
        vm.restorePurchase()
        vm.restorePurchase()
        advanceUntilIdle()

        coVerify(exactly = 1) { repo.restorePurchaseNow() }
    }

    @Test
    fun `a finished restore allows a new attempt`() = runTest2 {
        val repo = mockRepo()
        coEvery { repo.restorePurchaseNow() } returns UpgradeRepoGplay.Info(billingData = null)
        val vm = createVm(repo)

        vm.restorePurchase()
        advanceUntilIdle()
        vm.restorePurchase()
        advanceUntilIdle()

        coVerify(exactly = 2) { repo.restorePurchaseNow() }
    }

    // --- Switch-to-IAP gate ---

    @Test
    fun `every IAP tap runs the fresh SUBS gate, even for an apparent non-owner`() = runTest2 {
        // upgradeInfo says non-owner (stale/empty early state), but the fresh query finds a
        // renewing subscription — the launch must stay blocked.
        val repo = mockRepo()
        coEvery { repo.queryCurrentSubscriptions() } returns
            listOf(mockPurchase(CapodSku.Sub.PRO_UPGRADE.id, autoRenewing = true))
        val vm = createVm(repo)

        val event = async { vm.events.first() }
        vm.onGoIap(mockk<Activity>())
        advanceUntilIdle()

        event.await() shouldBe UpgradeViewModel.UpgradeEvent.SubscriptionStillRenewing
        coVerify(exactly = 1) { repo.queryCurrentSubscriptions() }
        coVerify(exactly = 0) { repo.launchBillingFlow(any(), any(), any()) }
    }

    @Test
    fun `IAP launch proceeds when no subscription is renewing`() = runTest2 {
        val repo = mockRepo()
        coEvery { repo.queryCurrentSubscriptions() } returns
            listOf(mockPurchase(CapodSku.Sub.PRO_UPGRADE.id, autoRenewing = false))
        val vm = createVm(repo)

        vm.onGoIap(mockk<Activity>())
        advanceUntilIdle()

        coVerify(exactly = 1) { repo.launchBillingFlow(any(), CapodSku.Iap.PRO_UPGRADE, null) }
    }

    @Test
    fun `a timed-out subscription check fails closed`() = runTest2 {
        val repo = mockRepo()
        coEvery { repo.queryCurrentSubscriptions() } coAnswers {
            delay(UpgradeViewModel.VERIFY_TIMEOUT_MS * 2)
            emptyList()
        }
        val vm = createVm(repo)

        val event = async { vm.events.first() }
        vm.onGoIap(mockk<Activity>())
        advanceUntilIdle()

        event.await() shouldBe UpgradeViewModel.UpgradeEvent.SubscriptionCheckFailed
        coVerify(exactly = 0) { repo.launchBillingFlow(any(), any(), any()) }
    }

    @Test
    fun `a failed subscription check fails closed with an error dialog`() = runTest2 {
        val repo = mockRepo()
        val boom = IllegalStateException("Play unavailable")
        coEvery { repo.queryCurrentSubscriptions() } throws boom
        val vm = createVm(repo)

        val forwardedError = async { vm.errorEvents.first() }
        vm.onGoIap(mockk<Activity>())
        advanceUntilIdle()

        forwardedError.await() shouldBe boom
        coVerify(exactly = 0) { repo.launchBillingFlow(any(), any(), any()) }
    }

    @Test
    fun `purchase actions are single-flight while the verification is suspended`() = runTest2 {
        val repo = mockRepo()
        coEvery { repo.queryCurrentSubscriptions() } coAnswers {
            delay(5_000)
            emptyList()
        }
        val vm = createVm(repo)

        vm.onGoIap(mockk<Activity>())
        vm.onGoIap(mockk<Activity>())
        vm.onGoSubscription(mockk<Activity>())
        advanceUntilIdle()

        coVerify(exactly = 1) { repo.queryCurrentSubscriptions() }
        // Only the IAP launch after its successful verification; the sub tap was swallowed.
        coVerify(exactly = 1) { repo.launchBillingFlow(any(), any(), any()) }
    }

    @Test
    fun `restore taps are ignored while a purchase action is in flight`() = runTest2 {
        // Symmetric exclusion: otherwise a verification resolving to a dialog and a concurrent
        // restore resolving to another dialog stack on screen.
        val repo = mockRepo()
        coEvery { repo.queryCurrentSubscriptions() } coAnswers {
            delay(5_000)
            emptyList()
        }
        val vm = createVm(repo)

        vm.onGoIap(mockk<Activity>())
        vm.restorePurchase()
        advanceUntilIdle()

        coVerify(exactly = 0) { repo.restorePurchaseNow() }
    }

    @Test
    fun `resume refreshes the subscription state for sub owners`() = runTest2 {
        // Returning from Play's Manage page must unlock the switch promptly — the global
        // foreground refresh is throttled to once an hour.
        val repo = mockRepo()
        every { repo.upgradeInfo } returns MutableStateFlow(
            ownerInfo(mockPurchase(CapodSku.Sub.PRO_UPGRADE.id, autoRenewing = true))
        )
        val vm = createVm(repo)
        vm.state.first { it is UpgradeUiState.Loaded }

        vm.onResume()
        advanceUntilIdle()

        coVerify(exactly = 1) { repo.queryCurrentSubscriptions() }
    }

    @Test
    fun `resume does not query Play for non-subscribers`() = runTest2 {
        val repo = mockRepo()
        val vm = createVm(repo)
        vm.state.first { it is UpgradeUiState.Loaded }

        vm.onResume()
        advanceUntilIdle()

        coVerify(exactly = 0) { repo.queryCurrentSubscriptions() }
    }

    @Test
    fun `purchase taps are ignored while a restore is running`() = runTest2 {
        val repo = mockRepo()
        coEvery { repo.restorePurchaseNow() } coAnswers {
            delay(5_000)
            UpgradeRepoGplay.Info(gracePeriod = true, billingData = null)
        }
        val vm = createVm(repo)

        vm.restorePurchase()
        vm.onGoIap(mockk<Activity>())
        advanceUntilIdle()

        coVerify(exactly = 0) { repo.queryCurrentSubscriptions() }
        coVerify(exactly = 0) { repo.launchBillingFlow(any(), any(), any()) }
    }

    @Test
    fun `user canceling the billing flow stays silent`() = runTest2 {
        val repo = mockRepo()
        coEvery { repo.launchBillingFlow(any(), any(), any()) } throws
            UserCanceledBillingException(RuntimeException("launch result"))
        val vm = createVm(repo)

        val errors = mutableListOf<Throwable>()
        val errorJob = launch(UnconfinedTestDispatcher(testScheduler)) { vm.errorEvents.collect { errors.add(it) } }

        vm.onGoIap(mockk<Activity>())
        advanceUntilIdle()

        coVerify(exactly = 1) { repo.launchBillingFlow(any(), any(), any()) }
        errors shouldBe emptyList()

        errorJob.cancel()
    }

    @Test
    fun `billing flow launch errors are forwarded to the error dialog`() = runTest2 {
        val repo = mockRepo()
        val boom = IllegalStateException("launch failed")
        coEvery { repo.launchBillingFlow(any(), any(), any()) } throws boom
        val vm = createVm(repo)

        val forwardedError = async { vm.errorEvents.first() }
        vm.onGoSubscription(mockk<Activity>())
        advanceUntilIdle()

        forwardedError.await() shouldBe boom
    }

    // --- Route handling / auto-close ---

    @Test
    fun `the sales route closes once the user is pro`() = runTest2 {
        val repo = mockRepo()
        every { repo.upgradeInfo } returns MutableStateFlow(
            UpgradeRepoGplay.Info(gracePeriod = true, billingData = null)
        )
        val vm = createVm(repo, manage = null)

        val navEvent = async { vm.navEvents.first() }
        vm.initialize(manage = false)
        advanceUntilIdle()

        navEvent.await() shouldBe NavEvent.Up
    }

    @Test
    fun `the manage route never auto-closes`() = runTest2 {
        val repo = mockRepo()
        every { repo.upgradeInfo } returns MutableStateFlow(
            UpgradeRepoGplay.Info(gracePeriod = true, billingData = null)
        )
        val vm = createVm(repo, manage = true)

        val navEvents = mutableListOf<NavEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.navEvents.collect { navEvents.add(it) } }
        advanceUntilIdle()

        navEvents shouldBe emptyList()

        job.cancel()
    }

    @Test
    fun `no auto-close before the route is bound`() = runTest2 {
        val repo = mockRepo()
        every { repo.upgradeInfo } returns MutableStateFlow(
            UpgradeRepoGplay.Info(gracePeriod = true, billingData = null)
        )
        val vm = createVm(repo, manage = null)

        val navEvents = mutableListOf<NavEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.navEvents.collect { navEvents.add(it) } }
        advanceUntilIdle()

        navEvents shouldBe emptyList()

        job.cancel()
    }

    // --- State mapping ---

    @Test
    fun `owners render price-independently while SKU queries hang`() = runTest2 {
        val repo = mockRepo()
        every { repo.upgradeInfo } returns MutableStateFlow(
            ownerInfo(mockPurchase(CapodSku.Sub.PRO_UPGRADE.id, autoRenewing = true))
        )
        coEvery { repo.querySkus(any()) } coAnswers { awaitCancellation() }
        val vm = createVm(repo)

        val state = vm.state.first { it is UpgradeUiState.Loaded }

        state.shouldBeInstanceOf<UpgradeUiState.Loaded>()
        state.ownership.subscription.shouldNotBeNull()
        state.ownership.subscription!!.isAutoRenewing shouldBe true
        state.iapPrice.shouldBeNull()
    }

    @Test
    fun `grace users see the quiet hint before the diagnostics threshold`() = runTest2 {
        val repo = mockRepo()
        every { repo.upgradeInfo } returns MutableStateFlow(
            UpgradeRepoGplay.Info(gracePeriod = true, billingData = null)
        )
        every { repo.proUnconfirmedSince } returns MutableStateFlow(now() - Duration.ofHours(1).toMillis())
        val vm = createVm(repo)

        val state = vm.state.first { it is UpgradeUiState.Loaded } as UpgradeUiState.Loaded

        state.grace.shouldNotBeNull()
        state.grace!!.showDiagnostics shouldBe false
    }

    @Test
    fun `grace escalates to diagnostics after the threshold`() = runTest2 {
        val repo = mockRepo()
        every { repo.upgradeInfo } returns MutableStateFlow(
            UpgradeRepoGplay.Info(gracePeriod = true, billingData = null)
        )
        every { repo.proUnconfirmedSince } returns MutableStateFlow(now() - Duration.ofHours(25).toMillis())
        val vm = createVm(repo)

        val state = vm.state.first { it is UpgradeUiState.Loaded } as UpgradeUiState.Loaded

        state.grace.shouldNotBeNull()
        state.grace!!.showDiagnostics shouldBe true
    }

    @Test
    fun `the grace tick re-evaluates when the episode crosses the threshold`() = runTest2 {
        val repo = mockRepo()
        every { repo.upgradeInfo } returns MutableStateFlow(
            UpgradeRepoGplay.Info(gracePeriod = true, billingData = null)
        )
        // 2 minutes before the diagnostics threshold.
        every { repo.proUnconfirmedSince } returns MutableStateFlow(
            now() - UpgradeViewModel.GRACE_DIAGNOSTICS_AFTER_MS + Duration.ofMinutes(2).toMillis()
        )
        val vm = createVm(repo)

        val states = mutableListOf<UpgradeUiState>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { states.add(it) } }
        // runCurrent, NOT advanceUntilIdle: idling would fast-forward the virtual clock through
        // the tick's delay while the wall-clock TestTimeSource still sits before the threshold.
        testScheduler.runCurrent()

        (states.last() as UpgradeUiState.Loaded).grace!!.showDiagnostics shouldBe false

        // All other combined flows are distinct-until-changed — only the tick can re-fire.
        timeSource.advanceBy(Duration.ofMinutes(3))
        testScheduler.advanceTimeBy(Duration.ofMinutes(3).toMillis())
        testScheduler.runCurrent()

        (states.last() as UpgradeUiState.Loaded).grace!!.showDiagnostics shouldBe true

        job.cancel()
    }

    @Test
    fun `no grace hint for confirmed owners`() = runTest2 {
        val repo = mockRepo()
        every { repo.upgradeInfo } returns MutableStateFlow(
            ownerInfo(mockPurchase(CapodSku.Iap.PRO_UPGRADE.id))
        )
        every { repo.proUnconfirmedSince } returns MutableStateFlow(now() - Duration.ofHours(25).toMillis())
        val vm = createVm(repo)

        val state = vm.state.first { it is UpgradeUiState.Loaded } as UpgradeUiState.Loaded

        state.grace.shouldBeNull()
        state.ownership.hasIap shouldBe true
    }

    @Test
    fun `banner shows for a previously-pro install that is no longer pro`() = runTest2 {
        val repo = mockRepo()
        every { repo.wasEverPro } returns MutableStateFlow(true)
        val vm = createVm(repo)

        val state = vm.state.first { it is UpgradeUiState.Loaded } as UpgradeUiState.Loaded

        state.showRestoreBanner shouldBe true
    }

    @Test
    fun `banner stays hidden while grace still keeps the user pro`() = runTest2 {
        val repo = mockRepo()
        every { repo.wasEverPro } returns MutableStateFlow(true)
        every { repo.upgradeInfo } returns MutableStateFlow(
            UpgradeRepoGplay.Info(gracePeriod = true, billingData = null)
        )
        val vm = createVm(repo)

        val state = vm.state.first { it is UpgradeUiState.Loaded } as UpgradeUiState.Loaded

        state.showRestoreBanner shouldBe false
    }

    @Test
    fun `purchase buttons stay disabled until billing has settled`() = runTest2 {
        val repo = mockRepo()
        val settledFlow = MutableStateFlow(false)
        every { repo.isSettled } returns settledFlow
        val vm = createVm(repo)

        val states = mutableListOf<UpgradeUiState>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { states.add(it) } }
        // runCurrent, NOT advanceUntilIdle: idling would run the bounded settle-fallback timer
        // and defeat the point of the test.
        testScheduler.runCurrent()

        (states.last() as UpgradeUiState.Loaded).iapEnabled shouldBe false
        (states.last() as UpgradeUiState.Loaded).subscriptionEnabled shouldBe false

        settledFlow.value = true
        testScheduler.runCurrent()

        (states.last() as UpgradeUiState.Loaded).iapEnabled shouldBe true

        job.cancel()
    }

    // --- Pure mappers ---

    @Test
    fun `toOwnership is conservative about auto-renewal`() {
        // Two records for the sub SKU, one still claiming renewal -> treated as renewing.
        val renewing = mockPurchase(CapodSku.Sub.PRO_UPGRADE.id, autoRenewing = true)
        val notRenewing = mockPurchase(CapodSku.Sub.PRO_UPGRADE.id, autoRenewing = false)
        val info = ownerInfo(notRenewing, renewing)

        val ownership = info.toOwnership()

        ownership.subscription.shouldNotBeNull()
        ownership.subscription!!.isAutoRenewing shouldBe true
        ownership.hasIap shouldBe false
    }

    @Test
    fun `toOwnership maps both product types`() {
        val info = ownerInfo(
            mockPurchase(CapodSku.Iap.PRO_UPGRADE.id),
            mockPurchase(CapodSku.Sub.PRO_UPGRADE.id, autoRenewing = false),
        )

        val ownership = info.toOwnership()

        ownership.hasIap shouldBe true
        ownership.subscription.shouldNotBeNull()
        ownership.subscription!!.isAutoRenewing shouldBe false
        ownership.ownsAnything shouldBe true
    }

    @Test
    fun `toLoadedState disables owned products`() {
        val state = toLoadedState(
            skus = SkuQueryState(done = true),
            ownership = Ownership(hasIap = true, subscription = SubscriptionOwnership(isAutoRenewing = true)),
            grace = null,
            showRestoreBanner = false,
            settled = true,
            restoreInProgress = false,
            verificationInProgress = false,
        )

        state.iapEnabled shouldBe false
        state.subscriptionEnabled shouldBe false
    }
}
