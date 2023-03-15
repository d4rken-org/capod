package eu.darken.capod.common.upgrade.core.data

import com.android.billingclient.api.Purchase

data class BillingData(
    val purchases: Collection<Purchase>
) {
    val purchasedSkus: Collection<PurchasedSku>
        get() = purchases.map { it.toPurchasedSku() }.flatten()

    private fun Purchase.toPurchasedSku(): Collection<PurchasedSku> = skus.map {
        PurchasedSku(Sku(it), this)
    }
}