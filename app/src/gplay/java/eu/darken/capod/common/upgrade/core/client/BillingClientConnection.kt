package eu.darken.capod.common.upgrade.core.client

import android.app.Activity
import com.android.billingclient.api.*
import eu.darken.capod.common.debug.logging.Logging.Priority.INFO
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.setupCommonEventHandlers
import eu.darken.capod.common.upgrade.core.data.Sku
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

data class BillingClientConnection(
    private val client: BillingClient,
    private val purchasesGlobal: Flow<Collection<Purchase>>,
) {
    private val purchasesLocal = MutableStateFlow<Collection<Purchase>>(emptySet())
    val purchases: Flow<Collection<Purchase>> = combine(purchasesGlobal, purchasesLocal) { global, local ->
        val combined = mutableMapOf<String, Purchase>()
        global.plus(local).toSet().sortedByDescending { it.purchaseTime }.forEach { purchase ->
            combined[purchase.orderId] = purchase
        }
        combined.values
    }
        .setupCommonEventHandlers(TAG) { "purchases" }

    suspend fun queryPurchases(): Collection<Purchase> {
        val (result: BillingResult, purchases) = suspendCoroutine<Pair<BillingResult, Collection<Purchase>?>> { continuation ->
            client.queryPurchasesAsync(BillingClient.SkuType.INAPP) { result, purchases ->
                continuation.resume(result to purchases)
            }
        }

        log(TAG) { "queryPurchases(): code=${result.responseCode}, message=${result.debugMessage}, purchases=$purchases" }

        if (!result.isSuccess) {
            log(TAG, WARN) { "queryPurchases() failed" }
            throw  BillingResultException(result)
        } else {
            requireNotNull(purchases)
        }

        purchasesLocal.value = purchases
        return purchases
    }

    suspend fun acknowledgePurchase(purchase: Purchase) {
        val ack = AcknowledgePurchaseParams.newBuilder().apply {
            setPurchaseToken(purchase.purchaseToken)
        }.build()

        val result = suspendCoroutine<BillingResult> { continuation ->
            client.acknowledgePurchase(ack) { continuation.resume(it) }
        }

        log(TAG, INFO) { "acknowledgePurchase($purchase): code=${result.responseCode} (${result.debugMessage})" }

        if (!result.isSuccess) throw BillingResultException(result)
    }

    suspend fun querySku(sku: Sku): Sku.Details {
        val productDetails = QueryProductDetailsParams.Product.newBuilder().apply {
            setProductType(BillingClient.ProductType.INAPP)
            setProductId(sku.id)
        }.build()

        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(productDetails)).build()

        val (result, details) = suspendCoroutine<Pair<BillingResult, Collection<ProductDetails>?>> { continuation ->
            client.queryProductDetailsAsync(params) { result, skuDetails ->
                continuation.resume(result to skuDetails)
            }
        }

        log(TAG) {
            "querySku(sku=$sku): code=${result.responseCode}, debug=${result.debugMessage}), skuDetails=$details"
        }

        if (!result.isSuccess) throw BillingResultException(result)

        if (details.isNullOrEmpty()) throw IllegalStateException("Unknown SKU, no details available.")

        return Sku.Details(sku, details)
    }

    suspend fun launchBillingFlow(activity: Activity, sku: Sku): BillingResult {
        log(TAG) { "launchBillingFlow(activity=$activity, sku=$sku)" }
        val skuDetails = querySku(sku)
        return launchBillingFlow(activity, skuDetails)
    }

    suspend fun launchBillingFlow(activity: Activity, skuDetails: Sku.Details): BillingResult {
        log(TAG) { "launchBillingFlow(activity=$activity, skuDetails=$skuDetails)" }

        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder().apply {
            setProductDetails(skuDetails.details.first())
        }.build()

        val billingFlowParams = BillingFlowParams.newBuilder().apply {
            setProductDetailsParamsList(listOf(productParams))
        }.build()

        return client.launchBillingFlow(activity, billingFlowParams)
    }

    companion object {
        val TAG: String = logTag("Upgrade", "Gplay", "Billing", "ClientConnection")
    }
}