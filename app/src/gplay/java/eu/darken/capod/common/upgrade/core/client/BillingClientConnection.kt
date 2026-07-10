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
import eu.darken.capod.common.upgrade.core.CapodSku
import eu.darken.capod.common.upgrade.core.data.Sku
import eu.darken.capod.common.upgrade.core.data.SkuDetails
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

data class BillingClientConnection(
    private val client: BillingClient,
    private val purchasesGlobal: Flow<Collection<Purchase>>,
) {
    private data class QueryCaches(
        val iaps: Collection<Purchase>? = null,
        val subs: Collection<Purchase>? = null,
    )

    private val queryCache = MutableStateFlow(QueryCaches())

    val purchases: Flow<Collection<Purchase>> = combine(
        purchasesGlobal,
        queryCache,
    ) { global, cached ->
        val combined = mutableSetOf<Purchase>()

        cached.iaps?.let { combined.addAll(it) }
        cached.subs?.let { combined.addAll(it) }

        global
            .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            .let { combined.addAll(it) }

        combined.sortedByDescending { it.purchaseTime }
    }
        .setupCommonEventHandlers(TAG) { "purchases" }

    // Returns the freshly queried PURCHASED purchases so callers get a guaranteed happens-before
    // relation instead of racing the shared purchases/billingData replay caches after a refresh.
    // Tolerant of a single product-type failure: a known Pro purchase found by either type is
    // authoritative, and an error only surfaces otherwise — so callers can tell "not owned" apart
    // from "couldn't verify".
    suspend fun refreshPurchases(): Collection<Purchase> = coroutineScope {
        val iapsDeferred = async { queryPurchasedProducts(BillingClient.ProductType.INAPP) }
        val subsDeferred = async { queryPurchasedProducts(BillingClient.ProductType.SUBS) }

        val iaps = iapsDeferred.await()
        val subs = subsDeferred.await()
        log(TAG) { "refreshPurchases(): iaps=${iaps.getOrNull()}, subs=${subs.getOrNull()}" }

        // Evaluate before publishing: an inconclusive refresh (query failed and nothing
        // authoritative found) must not touch the caches at all, or a partially updated state
        // could surface synthetic ownership to the hot purchases flow and wrongly refresh the
        // grace anchor from stale data.
        val combined = combinePurchaseResults(iaps, subs)

        // Single atomic snapshot update; a failed type retains its previous value.
        queryCache.update { previous ->
            QueryCaches(
                iaps = iaps.getOrNull() ?: previous.iaps,
                subs = subs.getOrNull() ?: previous.subs,
            )
        }

        combined
    }

    // Never throws except on cancellation, so a single failing product-type query doesn't cancel
    // the sibling query (or the coroutineScope).
    private suspend fun queryPurchasedProducts(
        productType: String,
    ): Result<Collection<Purchase>> = try {
        val purchased = queryPurchasesByType(productType)
            .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        Result.success(purchased)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
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

        // Combines the two product-type query results: with a partial failure, only a known Pro
        // purchase found by the successful type may suppress the error — an unknown/legacy purchase
        // must not mask that the other type couldn't be verified. Without failures, everything
        // found is returned as-is. Pure and unit-tested.
        internal fun combinePurchaseResults(
            iaps: Result<Collection<Purchase>>,
            subs: Result<Collection<Purchase>>,
            isAuthoritative: (Purchase) -> Boolean = { purchase ->
                purchase.products.any { productId -> CapodSku.PRO_SKUS.any { it.id == productId } }
            },
        ): Collection<Purchase> {
            val found = iaps.getOrNull().orEmpty() + subs.getOrNull().orEmpty()
            val error = iaps.exceptionOrNull() ?: subs.exceptionOrNull()
            return when {
                error == null || found.any(isAuthoritative) -> found.sortedByDescending { it.purchaseTime }
                else -> throw error
            }
        }
    }
}
