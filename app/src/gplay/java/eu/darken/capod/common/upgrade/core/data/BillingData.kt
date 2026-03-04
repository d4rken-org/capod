package eu.darken.capod.common.upgrade.core.data

import com.android.billingclient.api.Purchase
import eu.darken.capod.common.upgrade.core.CapodSku

data class BillingData(
    val purchases: Collection<Purchase>
) {
    val purchasedSkus: Collection<PurchasedSku>
        get() = purchases.flatMap { purchase ->
            purchase.products.mapNotNull { productId ->
                val sku = CapodSku.PRO_SKUS.singleOrNull { it.id == productId }
                sku?.let { PurchasedSku(it, purchase) }
            }
        }
}
