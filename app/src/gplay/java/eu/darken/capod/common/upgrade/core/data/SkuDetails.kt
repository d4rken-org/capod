package eu.darken.capod.common.upgrade.core.data

import com.android.billingclient.api.ProductDetails

data class SkuDetails(
    val sku: Sku,
    val details: ProductDetails,
)
