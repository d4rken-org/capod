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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class BillingClientConnection(
    private val client: BillingClient,
    private val purchasesGlobal: MutableStateFlow<Collection<Purchase>>,
    private val freshObservations: MutableSharedFlow<FreshPurchases>,
    private val freshFailuresGlobal: MutableSharedFlow<Unit>,
    private val purchaseFailuresGlobal: Flow<BillingResult>,
    private val listenerGeneration: () -> Long,
) {

    // Non-OK results from onPurchasesUpdated (e.g. async ITEM_ALREADY_OWNED after the Play sheet
    // opened). Consumed by a single persistent collector in UpgradeRepoGplay — not an event bus.
    val purchaseFailures: Flow<BillingResult> = purchaseFailuresGlobal

    // Every conclusive fresh look at PURCHASED purchases (successful queries and push payloads),
    // regardless of whether it differs from the previous one — the combined `purchases` state is
    // equality-deduped and can mix in stale listener data, so grace stamping must not use it.
    // Each observation carries provenance: only a full snapshot proves absence.
    val freshPurchases: Flow<FreshPurchases> = freshObservations

    // Failed attempts to get a fresh conclusive look (query errors, initial-query timeout).
    // Consumed by UpgradeRepoGplay to start the unconfirmed-episode clock — without this, a
    // sustained Play outage would never escalate the grace UI to its diagnostics stage.
    val freshFailures: Flow<Unit> = freshFailuresGlobal

    private data class QueryCaches(
        val iaps: Collection<Purchase>? = null,
        val subs: Collection<Purchase>? = null,
    )

    private val queryCache = MutableStateFlow(QueryCaches())

    // Serializes refreshes on this connection: the connect-time initial query, foreground
    // refreshes, manual restores, switch-gate verifications and already-owned recoveries may
    // overlap, and an older query completing late must not overwrite the cache with stale
    // purchases.
    private val refreshLock = Mutex()

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
    suspend fun refreshPurchases(): FreshPurchases = refreshLock.withLock {
        refreshPurchasesLocked()
    }

    private suspend fun refreshPurchasesLocked(): FreshPurchases = coroutineScope {
        val generationBefore = listenerGeneration()

        val iapsDeferred = async { queryPurchasedProducts(BillingClient.ProductType.INAPP) }
        val subsDeferred = async { queryPurchasedProducts(BillingClient.ProductType.SUBS) }

        val iaps = iapsDeferred.await()
        val subs = subsDeferred.await()
        log(TAG) { "refreshPurchases(): iaps=${iaps.getOrNull()}, subs=${subs.getOrNull()}" }

        // Evaluate before publishing: an inconclusive refresh (query failed and nothing
        // authoritative found) must not touch the caches at all, or a partially updated state
        // could surface synthetic ownership to the hot purchases flow and wrongly refresh the
        // grace anchor from stale data.
        val combined = try {
            combinePurchaseResults(iaps, subs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            freshFailuresGlobal.tryEmit(Unit)
            throw e
        }

        // Single atomic snapshot update; a failed type retains its previous value.
        queryCache.update { previous ->
            QueryCaches(
                iaps = iaps.getOrNull() ?: previous.iaps,
                subs = subs.getOrNull() ?: previous.subs,
            )
        }
        val bothOk = iaps.isSuccess && subs.isSuccess
        reconcileListenerRecords(
            fresh = combined,
            generationBefore = generationBefore,
            // A conclusive both-type query proves absence for every product: listener records it
            // didn't return are stale (refunded, expired) and must not linger until restart.
            absenceProven = bothOk,
            absenceScope = { true },
        )

        // Absence is only proven when both type queries succeeded AND no purchase event raced the
        // queries: a racing event either flips the generation (downgrading this to presence-only),
        // or its own fresh emission follows ours and immediately re-stamps the confirmation.
        val isFullSnapshot = bothOk && listenerGeneration() == generationBefore
        val fresh = FreshPurchases(combined, isFullSnapshot)

        // A conclusive refresh is a fresh observation for the grace stamping, even when the result
        // equals the previous one and the state flows dedupe it away.
        freshObservations.tryEmit(fresh)

        fresh
    }

    // Strict SUBS-only verification query for the switch-to-IAP gate: errors propagate (the
    // caller fails closed), and the result is committed to the caches so the ownership UI heals
    // from stale renewal state (e.g. after the user cancelled the subscription in Play).
    suspend fun querySubscriptions(): Collection<Purchase> = refreshLock.withLock {
        val generationBefore = listenerGeneration()
        val subs = try {
            queryPurchasesByType(BillingClient.ProductType.SUBS)
                .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            freshFailuresGlobal.tryEmit(Unit)
            throw e
        }

        queryCache.update { it.copy(subs = subs) }
        reconcileListenerRecords(
            fresh = subs,
            generationBefore = generationBefore,
            // A conclusive SUBS query proves absence for subscriptions only.
            absenceProven = true,
            absenceScope = { it.isKnownSub() },
        )

        // Presence-only provenance: a SUBS query proves nothing about the IAP.
        freshObservations.tryEmit(FreshPurchases(subs, isFullSnapshot = false))

        // Include listener-known subscription records the query doesn't cover (a purchase racing
        // the query survives reconciliation) — over-blocking the switch is safe, missing a
        // renewing sub is not.
        val listenerSubs = purchasesGlobal.value.filter { listenerRecord ->
            listenerRecord.purchaseState == Purchase.PurchaseState.PURCHASED &&
                listenerRecord.isKnownSub() &&
                subs.none { it.purchaseToken == listenerRecord.purchaseToken }
        }

        subs + listenerSubs
    }

    private fun Purchase.isKnownSub(): Boolean = products.any { it == CapodSku.Sub.PRO_UPGRADE.id }

    // Reconciles the listener overlay against a fresh query result — but ONLY when no purchase
    // event raced the query (generation unchanged, re-checked inside the CAS loop): a listener
    // record published mid-query is NEWER than the query result and must survive, or a
    // just-renewed subscription could be deleted by an older query and slip past the IAP gate.
    // Without a race: fresh records supersede same-token listener records (a stale in-session
    // isAutoRenewing=true must not coexist with newer query state forever), and when absence was
    // proven, in-scope listener records the query didn't return are dropped entirely (refunded or
    // expired purchases must not keep resurrecting until process restart).
    private fun reconcileListenerRecords(
        fresh: Collection<Purchase>,
        generationBefore: Long,
        absenceProven: Boolean,
        absenceScope: (Purchase) -> Boolean,
    ) {
        purchasesGlobal.update { current ->
            if (listenerGeneration() != generationBefore) return@update current
            current.filterNot { old ->
                fresh.any { it.purchaseToken == old.purchaseToken } || (absenceProven && absenceScope(old))
            }
        }
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

        // launchBillingFlow must run on the main thread (documented BillingClient contract), and
        // its RETURNED result reports whether the flow could be launched at all (ITEM_ALREADY_OWNED,
        // BILLING_UNAVAILABLE, DEVELOPER_ERROR, ...) — launch failures arrive here, not as
        // exceptions. Throw like the sibling methods do, so callers can surface them instead of
        // failing silently.
        val result = withContext(Dispatchers.Main) {
            client.launchBillingFlow(activity, billingFlowParams)
        }

        log(TAG) { "launchBillingFlow(sku=${sku.id}): code=${result.responseCode}, message=${result.debugMessage}" }

        if (!result.isSuccess) throw BillingResultException(result)

        return result
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
