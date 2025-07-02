package eu.darken.capod.common.upgrade.core.client

import android.app.Activity
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
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
        val combined = mutableSetOf<Purchase>()

        combined.addAll(local)

        global
            .let { purchases -> purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED } }
            .let { combined.addAll(it) }

        combined.sortedByDescending { it.purchaseTime }
    }
        .setupCommonEventHandlers(TAG) { "purchases" }

    suspend fun queryPurchases(): Collection<Purchase> {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        
        val (result: BillingResult, purchases) = suspendCoroutine<Pair<BillingResult, Collection<Purchase>?>> { continuation ->
            client.queryPurchasesAsync(params) { result, purchases ->
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

        val (result, details) = suspendCoroutine<Pair<BillingResult, List<ProductDetails>>> { continuation ->
            client.queryProductDetailsAsync(params) { billingResult, queryResult ->
                val productDetailsList = queryResult.productDetailsList ?: emptyList()
                continuation.resume(billingResult to productDetailsList)
            }
        }

        log(TAG) {
            "querySku(sku=$sku): code=${result.responseCode}, debug=${result.debugMessage}), skuDetails=$details"
        }

        if (!result.isSuccess) throw BillingResultException(result)

        if (details.isEmpty()) throw IllegalStateException("Unknown SKU, no details available.")

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