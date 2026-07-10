package eu.darken.capod.common.upgrade.core.client

import com.android.billingclient.api.Purchase
import eu.darken.capod.common.upgrade.core.CapodSku
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class BillingClientConnectionTest : BaseTest() {

    private fun mockPurchase(
        productId: String = CapodSku.Iap.PRO_UPGRADE.id,
        purchaseTime: Long = 1_000,
    ): Purchase = mockk {
        every { products } returns listOf(productId)
        every { this@mockk.purchaseTime } returns purchaseTime
    }

    @Test
    fun `combines both product types, newest first`() {
        val older = mockPurchase(CapodSku.Iap.PRO_UPGRADE.id, purchaseTime = 1_000)
        val newer = mockPurchase(CapodSku.Sub.PRO_UPGRADE.id, purchaseTime = 2_000)

        BillingClientConnection.combinePurchaseResults(
            iaps = Result.success(listOf(older)),
            subs = Result.success(listOf(newer)),
        ) shouldBe listOf(newer, older)
    }

    @Test
    fun `a single product-type failure does not mask a pro purchase found by the other`() {
        val owned = mockPurchase(CapodSku.Iap.PRO_UPGRADE.id)

        BillingClientConnection.combinePurchaseResults(
            iaps = Result.success(listOf(owned)),
            subs = Result.failure(RuntimeException("SUBS query failed")),
        ) shouldBe listOf(owned)

        val ownedSub = mockPurchase(CapodSku.Sub.PRO_UPGRADE.id)
        BillingClientConnection.combinePurchaseResults(
            iaps = Result.failure(RuntimeException("IAP query failed")),
            subs = Result.success(listOf(ownedSub)),
        ) shouldBe listOf(ownedSub)
    }

    @Test
    fun `an unknown purchase does not suppress the other product-type's failure`() {
        // An unknown/legacy product is discarded by BillingData later — it must not hide that the
        // other product type couldn't be verified, or a restore would wrongly report "not owned".
        val unknown = mockPurchase("some.legacy.product")

        shouldThrow<RuntimeException> {
            BillingClientConnection.combinePurchaseResults(
                iaps = Result.success(listOf(unknown)),
                subs = Result.failure(RuntimeException("SUBS query failed")),
            )
        }
    }

    @Test
    fun `unknown purchases are returned as-is when both queries succeed`() {
        val unknown = mockPurchase("some.legacy.product")

        BillingClientConnection.combinePurchaseResults(
            iaps = Result.success(listOf(unknown)),
            subs = Result.success(emptyList()),
        ) shouldBe listOf(unknown)
    }

    @Test
    fun `both product types empty returns empty`() {
        BillingClientConnection.combinePurchaseResults(
            iaps = Result.success(emptyList()),
            subs = Result.success(emptyList()),
        ) shouldBe emptyList()
    }

    @Test
    fun `nothing found but a query failed rethrows the error`() {
        shouldThrow<RuntimeException> {
            BillingClientConnection.combinePurchaseResults(
                iaps = Result.success(emptyList()),
                subs = Result.failure(RuntimeException("SUBS query failed")),
            )
        }

        shouldThrow<RuntimeException> {
            BillingClientConnection.combinePurchaseResults(
                iaps = Result.failure(RuntimeException("IAP query failed")),
                subs = Result.success(emptyList()),
            )
        }
    }
}
