package eu.darken.capod.common.upgrade.core.data

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
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
