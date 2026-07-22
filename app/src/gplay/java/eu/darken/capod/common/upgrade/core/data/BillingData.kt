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

// Provenance-tagged fresh billing data: only a full snapshot (both product types queried
// conclusively, no racing purchase event) proves absence — anything else proves presence only.
// The grace machinery relies on this to never start an unconfirmed episode from partial data.
data class FreshBillingData(
    val data: BillingData,
    val isFullSnapshot: Boolean,
)
