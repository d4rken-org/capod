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
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import eu.darken.capod.common.debug.logging.Logging.Priority.INFO
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.setupCommonEventHandlers
import eu.darken.capod.common.upgrade.core.data.Sku
import eu.darken.capod.common.upgrade.core.data.SkuDetails
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

data class BillingClientConnection(
    private val client: BillingClient,
    private val purchasesGlobal: Flow<Collection<Purchase>>,
) {
    private val queryCacheIaps = MutableStateFlow<Collection<Purchase>?>(null)
    private val queryCacheSubs = MutableStateFlow<Collection<Purchase>?>(null)

    val purchases: Flow<Collection<Purchase>> = combine(
        purchasesGlobal,
        queryCacheIaps,
        queryCacheSubs,
    ) { global, iaps, subs ->
        val combined = mutableSetOf<Purchase>()

        iaps?.let { combined.addAll(it) }
        subs?.let { combined.addAll(it) }

        global
            .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            .let { combined.addAll(it) }

        combined.sortedByDescending { it.purchaseTime }
    }
        .setupCommonEventHandlers(TAG) { "purchases" }

    suspend fun refreshPurchases(): Collection<Purchase> = coroutineScope {
        val iapsDeferred = async { queryPurchasesByType(BillingClient.ProductType.INAPP) }
        val subsDeferred = async { queryPurchasesByType(BillingClient.ProductType.SUBS) }

        val iaps = iapsDeferred.await()
        val subs = subsDeferred.await()

        queryCacheIaps.value = iaps
        queryCacheSubs.value = subs

        (iaps + subs).sortedByDescending { it.purchaseTime }
    }

    private suspend fun queryPurchasesByType(productType: String): Collection<Purchase> {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(productType)
            .build()

        val queryResult = client.queryPurchasesAsync(params)

        log(TAG) { "queryPurchases($productType): code=${queryResult.billingResult.responseCode}, message=${queryResult.billingResult.debugMessage}, purchases=${queryResult.purchasesList}" }

        if (!queryResult.billingResult.isSuccess) {
            log(TAG, WARN) { "queryPurchases($productType) failed" }
            throw BillingResultException(queryResult.billingResult)
        }

        return queryResult.purchasesList
    }

    suspend fun acknowledgePurchase(purchase: Purchase) {
        val ack = AcknowledgePurchaseParams.newBuilder().apply {
            setPurchaseToken(purchase.purchaseToken)
        }.build()

        val result = client.acknowledgePurchase(ack)

        log(TAG, INFO) { "acknowledgePurchase($purchase): code=${result.responseCode} (${result.debugMessage})" }

        if (!result.isSuccess) throw BillingResultException(result)
    }

    suspend fun querySkus(vararg skus: Sku): Collection<SkuDetails> {
        val byType = skus.groupBy { it.type }
        val results = mutableListOf<SkuDetails>()

        for ((type, typeSkus) in byType) {
            val productType = when (type) {
                Sku.Type.IAP -> BillingClient.ProductType.INAPP
                Sku.Type.SUBSCRIPTION -> BillingClient.ProductType.SUBS
            }

            val products = typeSkus.map { sku ->
                QueryProductDetailsParams.Product.newBuilder().apply {
                    setProductType(productType)
                    setProductId(sku.id)
                }.build()
            }

            val params = QueryProductDetailsParams.newBuilder().setProductList(products).build()

            val queryResult = client.queryProductDetails(params)

            val details = queryResult.productDetailsList.orEmpty()

            log(TAG) {
                "querySkus(type=$type, skus=${typeSkus.map { it.id }}): code=${queryResult.billingResult.responseCode}, debug=${queryResult.billingResult.debugMessage}, details=$details"
            }

            if (!queryResult.billingResult.isSuccess) throw BillingResultException(queryResult.billingResult)

            for (detail in details) {
                val matchingSku = typeSkus.firstOrNull { it.id == detail.productId }
                if (matchingSku != null) {
                    results.add(SkuDetails(matchingSku, detail))
                }
            }
        }

        return results
    }

    suspend fun launchBillingFlow(
        activity: Activity,
        sku: Sku,
        offer: Sku.Subscription.Offer? = null,
    ): BillingResult {
        log(TAG) { "launchBillingFlow(activity=$activity, sku=$sku, offer=$offer)" }

        val skuDetails = querySkus(sku).firstOrNull()
            ?: throw IllegalStateException("Unknown SKU, no details available for ${sku.id}")

        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder().apply {
            setProductDetails(skuDetails.details)
            if (sku is Sku.Subscription && offer != null) {
                val offerDetails = skuDetails.details.subscriptionOfferDetails
                    ?.firstOrNull { offer.matches(it) }
                if (offerDetails != null) {
                    setOfferToken(offerDetails.offerToken)
                }
            }
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
