package eu.darken.capod.common.upgrade.core

import com.android.billingclient.api.ProductDetails
import eu.darken.capod.common.upgrade.core.data.Sku
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class CapodSkuTest : BaseTest() {

    @Test
    fun `PRO_SKUS contains both IAP and subscription`() {
        CapodSku.PRO_SKUS.size shouldBe 2
        CapodSku.PRO_SKUS.any { it is Sku.Iap } shouldBe true
        CapodSku.PRO_SKUS.any { it is Sku.Subscription } shouldBe true
    }

    @Test
    fun `IAP SKU has correct type`() {
        CapodSku.Iap.PRO_UPGRADE.type shouldBe Sku.Type.IAP
    }

    @Test
    fun `Sub SKU has correct id and type`() {
        CapodSku.Sub.PRO_UPGRADE.id shouldBe "upgrade.pro"
        CapodSku.Sub.PRO_UPGRADE.type shouldBe Sku.Type.SUBSCRIPTION
    }

    @Test
    fun `Sub has both base and trial offers`() {
        CapodSku.Sub.PRO_UPGRADE.offers.size shouldBe 2
    }

    @Test
    fun `BASE_OFFER matches correct offer details`() {
        val offerDetails = mockk<ProductDetails.SubscriptionOfferDetails> {
            every { basePlanId } returns "upgrade-pro-baseplan"
            every { offerId } returns null
        }
        CapodSku.Sub.PRO_UPGRADE.BASE_OFFER.matches(offerDetails) shouldBe true
    }

    @Test
    fun `BASE_OFFER does not match wrong basePlanId`() {
        val offerDetails = mockk<ProductDetails.SubscriptionOfferDetails> {
            every { basePlanId } returns "wrong-plan"
            every { offerId } returns null
        }
        CapodSku.Sub.PRO_UPGRADE.BASE_OFFER.matches(offerDetails) shouldBe false
    }

    @Test
    fun `BASE_OFFER does not match offer with offerId`() {
        val offerDetails = mockk<ProductDetails.SubscriptionOfferDetails> {
            every { basePlanId } returns "upgrade-pro-baseplan"
            every { offerId } returns "some-offer"
        }
        CapodSku.Sub.PRO_UPGRADE.BASE_OFFER.matches(offerDetails) shouldBe false
    }

    @Test
    fun `TRIAL_OFFER matches correct offer details`() {
        val offerDetails = mockk<ProductDetails.SubscriptionOfferDetails> {
            every { basePlanId } returns "upgrade-pro-baseplan"
            every { offerId } returns "upgrade-pro-baseplan-trial"
        }
        CapodSku.Sub.PRO_UPGRADE.TRIAL_OFFER.matches(offerDetails) shouldBe true
    }

    @Test
    fun `TRIAL_OFFER does not match wrong offerId`() {
        val offerDetails = mockk<ProductDetails.SubscriptionOfferDetails> {
            every { basePlanId } returns "upgrade-pro-baseplan"
            every { offerId } returns "wrong-offer"
        }
        CapodSku.Sub.PRO_UPGRADE.TRIAL_OFFER.matches(offerDetails) shouldBe false
    }

    @Test
    fun `TRIAL_OFFER does not match null offerId`() {
        val offerDetails = mockk<ProductDetails.SubscriptionOfferDetails> {
            every { basePlanId } returns "upgrade-pro-baseplan"
            every { offerId } returns null
        }
        CapodSku.Sub.PRO_UPGRADE.TRIAL_OFFER.matches(offerDetails) shouldBe false
    }
}
