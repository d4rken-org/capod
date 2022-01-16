package eu.darken.capod.common.upgrade.core

import com.android.billingclient.api.Purchase
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.upgrade.core.data.PurchasedSku
import eu.darken.capod.common.upgrade.core.data.Sku


fun Purchase.toPurchasedSku(): Collection<PurchasedSku> = skus.map {
    PurchasedSku(Sku(it), this)
}

private val TAG: String = logTag("Upgrade", "Gplay", "Billing", "Extensions")
