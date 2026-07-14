package eu.darken.capod.common.upgrade.core.data

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import eu.darken.capod.common.AppForegroundState
import eu.darken.capod.common.upgrade.core.client.BillingClientConnection
import eu.darken.capod.common.upgrade.core.client.BillingClientConnectionProvider
import eu.darken.capod.common.upgrade.core.client.BillingException
import eu.darken.capod.common.upgrade.core.client.BillingResultException
import eu.darken.capod.common.upgrade.core.client.GplayServiceUnavailableException
import eu.darken.capod.common.upgrade.core.client.ItemAlreadyOwnedBillingException
import eu.darken.capod.common.upgrade.core.client.UserCanceledBillingException
import eu.darken.capod.common.upgrade.core.data.BillingDataRepo.Companion.tryMapUserFriendly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.TestTimeSource
import testhelpers.coroutine.runTest2
import java.time.Duration

class BillingDataRepoTest : BaseTest() {

    private fun mockBillingResult(responseCode: Int): BillingResult = mockk {
        every { this@mockk.responseCode } returns responseCode
        every { debugMessage } returns "mock"
    }

    @Test
    fun `temporary unavailable maps to GplayServiceUnavailableException`() {
        val result = mockBillingResult(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE)
        val mapped = BillingResultException(result).tryMapUserFriendly()
        mapped.shouldBeInstanceOf<GplayServiceUnavailableException>()
    }

    @Test
    fun `service disconnected maps to GplayServiceUnavailableException`() {
        val result = mockBillingResult(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED)
        val mapped = BillingResultException(result).tryMapUserFriendly()
        mapped.shouldBeInstanceOf<GplayServiceUnavailableException>()
    }

    @Test
    fun `service timeout maps to GplayServiceUnavailableException`() {
        val result = mockBillingResult(BillingClient.BillingResponseCode.SERVICE_TIMEOUT)
        val mapped = BillingResultException(result).tryMapUserFriendly()
        mapped.shouldBeInstanceOf<GplayServiceUnavailableException>()
    }

    @Test
    fun `permanent unavailable maps to GplayServiceUnavailableException`() {
        val result = mockBillingResult(BillingClient.BillingResponseCode.BILLING_UNAVAILABLE)
        val mapped = BillingResultException(result).tryMapUserFriendly()
        mapped.shouldBeInstanceOf<GplayServiceUnavailableException>()
    }

    @Test
    fun `network error maps to GplayServiceUnavailableException`() {
        val result = mockBillingResult(BillingClient.BillingResponseCode.NETWORK_ERROR)
        val mapped = BillingResultException(result).tryMapUserFriendly()
        mapped.shouldBeInstanceOf<GplayServiceUnavailableException>()
    }

    @Test
    @Suppress("DEPRECATION")
    fun `transient launch failures are not bug-reported, actionable ones are`() {
        with(BillingDataRepo.IGNORED_LAUNCH_CODES) {
            // Expected user/environmental situations — the user already sees proper UI for these.
            contains(BillingClient.BillingResponseCode.USER_CANCELED) shouldBe true
            contains(BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) shouldBe true
            contains(BillingClient.BillingResponseCode.BILLING_UNAVAILABLE) shouldBe true
            contains(BillingClient.BillingResponseCode.ERROR) shouldBe true
            contains(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE) shouldBe true
            contains(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED) shouldBe true
            contains(BillingClient.BillingResponseCode.SERVICE_TIMEOUT) shouldBe true
            contains(BillingClient.BillingResponseCode.NETWORK_ERROR) shouldBe true
            contains(BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED) shouldBe true
            // Actionable defects must keep reporting.
            contains(BillingClient.BillingResponseCode.DEVELOPER_ERROR) shouldBe false
            contains(BillingClient.BillingResponseCode.ITEM_UNAVAILABLE) shouldBe false
        }
    }

    @Test
    fun `connection retry uses capped backoff and retries early on foreground entry`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        var attempts = 0
        val provider = mockk<BillingClientConnectionProvider> {
            every { connection } returns flow {
                attempts++
                throw BillingException("still broken")
            }
        }
        val foreground = MutableStateFlow(false)
        val foregroundState = mockk<AppForegroundState> {
            every { isForeground } returns foreground
        }

        try {
            BillingDataRepo(provider, testScope, foregroundState, TestTimeSource())
            testScope.testScheduler.runCurrent()
            attempts shouldBe 1

            // First backoff is 60s — not a second sooner.
            testScope.testScheduler.advanceTimeBy(59_000)
            testScope.testScheduler.runCurrent()
            attempts shouldBe 1
            testScope.testScheduler.advanceTimeBy(2_000)
            testScope.testScheduler.runCurrent()
            attempts shouldBe 2

            // Second backoff would be 120s — a foreground entry short-circuits it, so a user
            // returning from e.g. Google sign-in doesn't wait out the full backoff. A foreground
            // entry drives two independent early-retry paths (the retry loop's own foreground
            // branch, plus the init foreground-refresh calling refresh() which emits a kick), so
            // the exact count is interleaving-dependent — either can produce attempt 3 or 4.
            testScope.testScheduler.advanceTimeBy(5_000)
            foreground.value = true
            testScope.testScheduler.runCurrent()
            (attempts in 3..4) shouldBe true

            // ...and then it settles: with no further action/lifecycle signal and time still well
            // under the next backoff, the double-kick must not compound into a busy-loop.
            val settled = attempts
            testScope.testScheduler.advanceTimeBy(5_000)
            testScope.testScheduler.runCurrent()
            attempts shouldBe settled
        } finally {
            // The repo pipelines and retry loop are infinite — a leaked scope after a failed
            // assertion would keep them alive for the rest of the JVM.
            testScope.cancel()
        }
    }

    @Test
    fun `explicit billing operations kick a waiting connection retry`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        var attempts = 0
        val provider = mockk<BillingClientConnectionProvider> {
            every { connection } returns flow {
                attempts++
                throw BillingException("still broken")
            }
        }
        val foregroundState = mockk<AppForegroundState> {
            every { isForeground } returns MutableStateFlow(false)
        }

        try {
            val repo = BillingDataRepo(provider, testScope, foregroundState, TestTimeSource())
            testScope.testScheduler.runCurrent()
            attempts shouldBe 1

            // A restore-style refresh() while the retry is waiting out its 60s backoff kicks it
            // immediately — the user shouldn't wait out the timer after fixing Play themselves.
            testScope.testScheduler.advanceTimeBy(5_000)
            val refreshJob = testScope.launch { runCatching { repo.refresh() } }
            testScope.testScheduler.runCurrent()
            attempts shouldBe 2

            refreshJob.cancel()
        } finally {
            testScope.cancel()
        }
    }

    @Test
    fun `user canceled maps to UserCanceledBillingException`() {
        val result = mockBillingResult(BillingClient.BillingResponseCode.USER_CANCELED)
        val mapped = BillingResultException(result).tryMapUserFriendly()
        mapped.shouldBeInstanceOf<UserCanceledBillingException>()
    }

    @Test
    fun `item already owned maps to ItemAlreadyOwnedBillingException`() {
        val result = mockBillingResult(BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED)
        val mapped = BillingResultException(result).tryMapUserFriendly()
        mapped.shouldBeInstanceOf<ItemAlreadyOwnedBillingException>()
    }

    @Test
    fun `other billing result passes through unchanged`() {
        val result = mockBillingResult(BillingClient.BillingResponseCode.DEVELOPER_ERROR)
        val original = BillingResultException(result)
        val mapped = original.tryMapUserFriendly()
        mapped shouldBe original
    }

    @Test
    fun `non-billing exception passes through unchanged`() {
        val original = IllegalStateException("something else")
        val mapped = original.tryMapUserFriendly()
        mapped shouldBe original
    }

    @Test
    fun `generic BillingException passes through unchanged`() {
        val original = BillingException("generic billing error")
        val mapped = original.tryMapUserFriendly()
        mapped shouldBe original
    }

    private fun mockPurchase(state: Int, acknowledged: Boolean): Purchase = mockk {
        every { purchaseState } returns state
        every { isAcknowledged } returns acknowledged
    }

    @Test
    fun `ack pipeline acknowledges only unacknowledged PURCHASED purchases`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        // A PENDING purchase must not be acknowledged (acking it fails and spins the retry loop);
        // an already-acknowledged one must not be re-acked; only a settled, unacked one is acked.
        val pending = mockPurchase(Purchase.PurchaseState.PENDING, acknowledged = false)
        val purchasedUnacked = mockPurchase(Purchase.PurchaseState.PURCHASED, acknowledged = false)
        val purchasedAcked = mockPurchase(Purchase.PurchaseState.PURCHASED, acknowledged = true)

        val clientConnection = mockk<BillingClientConnection> {
            every { purchases } returns flowOf(listOf(pending, purchasedUnacked, purchasedAcked))
            coEvery { acknowledgePurchase(any()) } returns Unit
        }
        val provider = mockk<BillingClientConnectionProvider> {
            every { connection } returns flowOf(clientConnection)
        }
        val foregroundState = mockk<AppForegroundState> {
            every { isForeground } returns MutableStateFlow(false)
        }

        try {
            BillingDataRepo(provider, testScope, foregroundState, TestTimeSource())
            testScope.testScheduler.runCurrent()

            coVerify(exactly = 1) { clientConnection.acknowledgePurchase(purchasedUnacked) }
            coVerify(exactly = 0) { clientConnection.acknowledgePurchase(pending) }
            coVerify(exactly = 0) { clientConnection.acknowledgePurchase(purchasedAcked) }
        } finally {
            testScope.cancel()
        }
    }

    private class ForegroundRefreshHarness(testScope: TestScope) {
        val clientConnection = mockk<BillingClientConnection> {
            every { purchases } returns emptyFlow()
            coEvery { refreshPurchases() } returns emptyList()
        }
        val provider = mockk<BillingClientConnectionProvider> {
            every { connection } returns flowOf(this@ForegroundRefreshHarness.clientConnection)
        }
        val foreground = MutableStateFlow(false)
        val foregroundState = mockk<AppForegroundState> {
            every { isForeground } returns foreground
        }
        val timeSource = TestTimeSource()
        val repo = BillingDataRepo(provider, testScope, foregroundState, timeSource)
    }

    @Test
    fun `coming to the foreground triggers a purchase refresh, throttled`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val harness = ForegroundRefreshHarness(testScope)

        harness.foreground.value = true
        testScope.testScheduler.advanceUntilIdle()
        coVerify(exactly = 1) { harness.clientConnection.refreshPurchases() }

        // Background/foreground again within the throttle window -> no additional query.
        harness.foreground.value = false
        harness.foreground.value = true
        testScope.testScheduler.advanceUntilIdle()
        coVerify(exactly = 1) { harness.clientConnection.refreshPurchases() }

        testScope.cancel()
    }

    @Test
    fun `foreground refresh runs again once the throttle window has passed`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val harness = ForegroundRefreshHarness(testScope)

        harness.foreground.value = true
        testScope.testScheduler.advanceUntilIdle()
        coVerify(exactly = 1) { harness.clientConnection.refreshPurchases() }

        harness.timeSource.advanceBy(Duration.ofMinutes(61))
        harness.foreground.value = false
        harness.foreground.value = true
        testScope.testScheduler.advanceUntilIdle()
        coVerify(exactly = 2) { harness.clientConnection.refreshPurchases() }

        testScope.cancel()
    }

    @Test
    fun `staying in the background never triggers a refresh`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val harness = ForegroundRefreshHarness(testScope)

        testScope.testScheduler.advanceUntilIdle()

        coVerify(exactly = 0) { harness.clientConnection.refreshPurchases() }

        testScope.cancel()
    }
}
