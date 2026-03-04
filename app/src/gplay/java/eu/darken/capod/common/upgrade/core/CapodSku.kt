package eu.darken.capod.common.upgrade.core

import eu.darken.capod.common.BuildConfigWrap
import eu.darken.capod.common.upgrade.core.data.Sku

interface CapodSku {

    interface Iap : CapodSku {
        object PRO_UPGRADE : Sku.Iap, Iap {
            override val id = "${BuildConfigWrap.APPLICATION_ID}.iap.upgrade.pro"
        }
    }

    interface Sub : CapodSku {
        object PRO_UPGRADE : Sku.Subscription, Sub {
            override val id = "upgrade.pro"
            override val offers = setOf(BASE_OFFER, TRIAL_OFFER)

            object BASE_OFFER : Sku.Subscription.Offer {
                override val basePlanId = "upgrade-pro-baseplan"
                override val offerId: String? = null
            }

            object TRIAL_OFFER : Sku.Subscription.Offer {
                override val basePlanId = "upgrade-pro-baseplan"
                override val offerId = "upgrade-pro-baseplan-trial"
            }
        }
    }

    companion object {
        val PRO_SKUS: Set<Sku> = setOf(Sub.PRO_UPGRADE, Iap.PRO_UPGRADE)
    }
}
