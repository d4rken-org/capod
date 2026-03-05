package eu.darken.capod.common.upgrade.core.data

import com.android.billingclient.api.Purchase
import eu.darken.capod.common.upgrade.core.CapodSku
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class BillingDataTest : BaseTest() {

    private fun mockPurchase(vararg productIds: String): Purchase = mockk {
        every { products } returns productIds.toList()
    }

    @Test
    fun `empty purchases yields empty purchasedSkus`() {
        val data = BillingData(purchases = emptyList())
        data.purchasedSkus.shouldBeEmpty()
    }

    @Test
    fun `purchase with IAP product ID maps to IAP SKU`() {
        val purchase = mockPurchase(CapodSku.Iap.PRO_UPGRADE.id)
        val data = BillingData(purchases = listOf(purchase))

        data.purchasedSkus.size shouldBe 1
        data.purchasedSkus.first().sku shouldBe CapodSku.Iap.PRO_UPGRADE
        data.purchasedSkus.first().purchase shouldBe purchase
    }

    @Test
    fun `purchase with subscription product ID maps to Sub SKU`() {
        val purchase = mockPurchase(CapodSku.Sub.PRO_UPGRADE.id)
        val data = BillingData(purchases = listOf(purchase))

        data.purchasedSkus.size shouldBe 1
        data.purchasedSkus.first().sku shouldBe CapodSku.Sub.PRO_UPGRADE
        data.purchasedSkus.first().purchase shouldBe purchase
    }

    @Test
    fun `purchase with unknown product ID is filtered out`() {
        val purchase = mockPurchase("com.unknown.product")
        val data = BillingData(purchases = listOf(purchase))

        data.purchasedSkus.shouldBeEmpty()
    }

    @Test
    fun `purchase with mixed products returns only matching SKU`() {
        val purchase = mockPurchase(CapodSku.Sub.PRO_UPGRADE.id, "com.unknown.product")
        val data = BillingData(purchases = listOf(purchase))

        data.purchasedSkus.size shouldBe 1
        data.purchasedSkus.first().sku shouldBe CapodSku.Sub.PRO_UPGRADE
    }

    @Test
    fun `multiple purchases with different pro SKUs`() {
        val iapPurchase = mockPurchase(CapodSku.Iap.PRO_UPGRADE.id)
        val subPurchase = mockPurchase(CapodSku.Sub.PRO_UPGRADE.id)
        val data = BillingData(purchases = listOf(iapPurchase, subPurchase))

        data.purchasedSkus.size shouldBe 2
        data.purchasedSkus.map { it.sku }.toSet() shouldBe setOf(CapodSku.Iap.PRO_UPGRADE, CapodSku.Sub.PRO_UPGRADE)
    }
}
