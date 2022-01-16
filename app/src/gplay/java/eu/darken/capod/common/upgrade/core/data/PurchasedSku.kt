package eu.darken.capod.common.upgrade.core.data

import com.android.billingclient.api.Purchase
import eu.darken.capod.common.upgrade.core.data.Sku

data class PurchasedSku(val sku: Sku, val purchase: Purchase) {
    override fun toString(): String = "IAP(sku=$sku, purchase=${purchase.skus})"
}