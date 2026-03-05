package eu.darken.capod.common.upgrade.core.data

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import eu.darken.capod.common.upgrade.core.client.BillingException
import eu.darken.capod.common.upgrade.core.client.BillingResultException
import eu.darken.capod.common.upgrade.core.client.GplayServiceUnavailableException
import eu.darken.capod.common.upgrade.core.data.BillingDataRepo.Companion.tryMapUserFriendly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

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
    fun `other billing result passes through unchanged`() {
        val result = mockBillingResult(BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED)
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
}
