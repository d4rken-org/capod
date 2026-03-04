package eu.darken.capod.common.upgrade.core.data

import com.android.billingclient.api.ProductDetails

interface Sku {
    val id: String
    val type: Type

    interface Iap : Sku {
        override val type: Type get() = Type.IAP
    }

    interface Subscription : Sku {
        override val type: Type get() = Type.SUBSCRIPTION
        val offers: Collection<Offer>

        interface Offer {
            val basePlanId: String
            val offerId: String?
            fun matches(target: ProductDetails.SubscriptionOfferDetails): Boolean =
                basePlanId == target.basePlanId && offerId == target.offerId
        }
    }

    enum class Type { IAP, SUBSCRIPTION }
}
