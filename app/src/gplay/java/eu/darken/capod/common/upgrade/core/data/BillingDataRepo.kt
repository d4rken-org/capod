package eu.darken.capod.common.upgrade.core.data

import android.app.Activity
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import eu.darken.capod.common.AppForegroundState
import eu.darken.capod.common.TimeSource
import eu.darken.capod.common.coroutine.AppScope
import eu.darken.capod.common.debug.Bugs
import eu.darken.capod.common.debug.logging.Logging.Priority.*
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.replayingShare
import eu.darken.capod.common.flow.setupCommonEventHandlers
import eu.darken.capod.common.upgrade.core.client.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillingDataRepo @Inject constructor(
    billingClientConnectionProvider: BillingClientConnectionProvider,
    @AppScope private val scope: CoroutineScope,
    private val appForegroundState: AppForegroundState,
    private val timeSource: TimeSource,
) {

    // Monotonic (elapsedRealtime) so wall-clock corrections can't extend the throttle window.
    // Null until the first attempt, so devices with less than an hour of uptime still refresh.
    private var lastForegroundRefreshAt: Long? = null

    // Explicit billing operations kick a waiting connection-retry backoff so a user action (restore
    // tap, buy tap, opening the upgrade screen) reconnects immediately after Play was fixed instead
    // of waiting out the timer. Zero replay: kicks only matter while a retry is actively waiting —
    // a healthy connection must not accumulate stale wake-ups. (Ported from sdmaid-se#2562.)
    private val connectionKicks = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val connectionProvider = billingClientConnectionProvider.connection
        .retryWhen { cause, attempt ->
            if (cause is CancellationException) return@retryWhen false
            log(TAG, ERROR) { "Unable to provide client connection (attempt=$attempt):\n${cause.asLog()}" }
            // Capped backoff: don't hammer a persistently broken Play from the always-hot process
            // (upstream already did 5 quick retries). An explicit billing operation or a foreground
            // *entry* short-circuits the wait — e.g. the user just returned from signing into the
            // missing Google account and shouldn't have to wait out the full backoff.
            val backoffMs = (RETRY_BACKOFF_BASE_MS * (attempt + 1)).coerceAtMost(RETRY_BACKOFF_MAX_MS)
            val kicked = withTimeoutOrNull(backoffMs) {
                merge(
                    connectionKicks,
                    // StateFlow dedupes, so after dropping the current value the next `true` is a
                    // real foreground *entry*, not the pre-existing foreground state.
                    appForegroundState.isForeground.drop(1).filter { it }.map { },
                ).first()
            }
            if (kicked != null) log(TAG) { "User action or foreground entry, retrying billing connection early" }
            true
        }
        .replayingShare(scope)

    val billingData: Flow<BillingData> = connectionProvider
        .flatMapLatest { it.purchases }
        .map { BillingData(purchases = it) }
        .setupCommonEventHandlers(TAG) { "billingData" }
        .replayingShare(scope)

    // Async purchase failures from onPurchasesUpdated; UpgradeRepoGplay reconciles
    // ITEM_ALREADY_OWNED silently.
    val purchaseFailures: Flow<BillingResult> = connectionProvider
        .flatMapLatest { it.purchaseFailures }
        .setupCommonEventHandlers(TAG) { "purchaseFailures" }

    // Every fresh observation of PURCHASED purchases (successful queries and push payloads) —
    // unlike billingData this is not equality-deduped state and never mixes in stale listener
    // data, so it is the only valid source for grace stamping. Carries provenance: only full
    // snapshots may prove absence.
    val freshBillingData: Flow<FreshBillingData> = connectionProvider
        .flatMapLatest { it.freshPurchases }
        .map { FreshBillingData(data = BillingData(purchases = it.purchases), isFullSnapshot = it.isFullSnapshot) }
        .setupCommonEventHandlers(TAG) { "freshBillingData" }

    // Local failures the connection can't see itself (e.g. a foreground refresh timing out while
    // the connection retry backoff is still waiting).
    private val localRefreshFailures = MutableSharedFlow<Unit>(extraBufferCapacity = 8)

    // Failed attempts to get fresh conclusive purchase data (query errors, timeouts). Consumed by
    // UpgradeRepoGplay to start the unconfirmed-episode clock during sustained Play outages.
    val refreshFailures: Flow<Unit> = merge(
        connectionProvider.flatMapLatest { it.freshFailures },
        localRefreshFailures,
    )

    // Tokens successfully acknowledged this process: the immutable Purchase snapshots in the
    // combined view keep claiming isAcknowledged=false until a fresh query supersedes them, and
    // every re-emission would otherwise re-ack the same purchase — harmless to Play (repeat acks
    // return OK) but a pointless extra IPC each time. Only recorded on SUCCESS, so a failed ack
    // stays retryable. Single sequential collector, no locking needed.
    private val ackedTokens = mutableSetOf<String>()

    init {
        connectionProvider
            .flatMapLatest { client ->
                client.purchases.map { client to it }
            }
            .onEach { (client, purchases) ->
                purchases
                    .filter {
                        // Only settled purchases can be acknowledged — acking a PENDING purchase
                        // fails and would spin the retry loop below.
                        val needsAck = !it.isAcknowledged &&
                            it.purchaseState == Purchase.PurchaseState.PURCHASED &&
                            it.purchaseToken !in ackedTokens

                        if (needsAck) log(TAG, INFO) { "Needs ACK: $it" }
                        else log(TAG) { "No ACK necessary: $it" }

                        needsAck
                    }
                    .forEach {
                        log(TAG, INFO) { "Acknowledging purchase: $it" }
                        client.acknowledgePurchase(it)
                        ackedTokens.add(it.purchaseToken)
                    }
            }
            .setupCommonEventHandlers(TAG) { "connection-acks" }
            .retryWhen { cause, attempt ->
                log(TAG, ERROR) { "Failed to acknowledge purchase: ${cause.asLog()}" }

                if (cause is CancellationException) {
                    log(TAG) { "Ack was cancelled (appScope?) cancelled." }
                    return@retryWhen false
                }

                if (attempt > 5) {
                    log(TAG, WARN) { "Reached attempt limit: $attempt due to $cause" }
                    return@retryWhen false
                }

                if (cause !is BillingException) {
                    log(TAG, WARN) { "Unknown exception type: $cause" }
                    return@retryWhen false
                }

                if (cause is BillingResultException && cause.result.isGplayUnavailablePermanent) {
                    log(TAG) { "Got BILLING_UNAVAILABLE while trying to ACK purchase." }
                    return@retryWhen false
                }

                log(TAG) { "Will retry ACK (attempt=$attempt)" }
                delay(3000 * attempt)
                true
            }
            .launchIn(scope)

        // Play only pushes onPurchasesUpdated for purchases made in this session, and the
        // connection (kept hot by App's AppScope subscriber) can live for the entire process
        // lifetime — without a re-query, refunds, cross-device purchases or lapsed subscriptions
        // are only noticed on app restart or manual restore. Google recommends re-querying
        // purchases when the app comes to the foreground.
        appForegroundState.isForeground
            .filter { it }
            .onEach {
                val now = timeSource.elapsedRealtime()
                val lastAt = lastForegroundRefreshAt
                if (lastAt != null && now - lastAt < FOREGROUND_REFRESH_THROTTLE_MS) {
                    log(TAG, VERBOSE) { "Foreground purchase refresh throttled" }
                    return@onEach
                }
                // Advanced per attempt, not per success — a broken Play should not be hammered
                // on every foreground transition.
                lastForegroundRefreshAt = now
                try {
                    // Bounded: an unavailable connection suspends refresh() indefinitely (60s
                    // retry loop) and would otherwise block all future foreground refreshes.
                    val result = withTimeoutOrNull(FOREGROUND_REFRESH_TIMEOUT_MS) { refresh() }
                    if (result != null) {
                        log(TAG) { "Foreground purchase refresh done" }
                    } else {
                        log(TAG, WARN) { "Foreground purchase refresh timed out" }
                        // The timeout cancels the query before its own failure path can signal —
                        // report it here so the unconfirmed-episode clock still starts.
                        localRefreshFailures.tryEmit(Unit)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log(TAG, WARN) { "Foreground purchase refresh failed: ${e.asLog()}" }
                }
            }
            .setupCommonEventHandlers(TAG) { "foreground-refresh" }
            .launchIn(scope)
    }

    suspend fun refresh(): FreshBillingData = try {
        connectionKicks.tryEmit(Unit)
        val clientConnection = connectionProvider.first()
        val fresh = clientConnection.refreshPurchases()

        FreshBillingData(data = BillingData(purchases = fresh.purchases), isFullSnapshot = fresh.isFullSnapshot)
    } catch (e: Exception) {
        throw e.tryMapUserFriendly()
    }

    // Strict SUBS-only ownership check for the switch-to-IAP gate: errors propagate so callers
    // can fail closed, and the fresh result is committed so stale renewal state heals.
    suspend fun querySubscriptions(): Collection<Purchase> = try {
        connectionKicks.tryEmit(Unit)
        val clientConnection = connectionProvider.first()
        clientConnection.querySubscriptions()
    } catch (e: Exception) {
        throw e.tryMapUserFriendly()
    }

    suspend fun querySkus(vararg skus: Sku): Collection<SkuDetails> = try {
        connectionKicks.tryEmit(Unit)
        val clientConnection = connectionProvider.first()
        clientConnection.querySkus(*skus)
    } catch (e: Exception) {
        throw e.tryMapUserFriendly()
    }

    suspend fun startBillingFlow(
        activity: Activity,
        sku: Sku,
        offer: Sku.Subscription.Offer? = null,
    ) {
        try {
            connectionKicks.tryEmit(Unit)
            val clientConnection = connectionProvider.first()
            clientConnection.launchBillingFlow(activity, sku, offer)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to start billing flow:\n${e.asLog()}" }
            if (e !is BillingResultException || e.result.responseCode !in IGNORED_LAUNCH_CODES) {
                Bugs.report(TAG, "Billing flow failed for $sku", e)
            }

            throw e.tryMapUserFriendly()
        }
    }

    companion object {
        val TAG: String = logTag("Upgrade", "Gplay", "Billing", "DataRepo")

        private const val FOREGROUND_REFRESH_THROTTLE_MS = 60 * 60 * 1000L // 1h
        private const val FOREGROUND_REFRESH_TIMEOUT_MS = 30_000L
        private const val RETRY_BACKOFF_BASE_MS = 60_000L
        private const val RETRY_BACKOFF_MAX_MS = 5 * 60_000L

        // Expected environmental/user situations — user-facing handling only, no bug report.
        // USER_CANCELED stays silent in the UI, ITEM_ALREADY_OWNED is auto-handled by
        // UpgradeRepoGplay (restore instead of error), the service/network codes are transient
        // connectivity states the user sees a proper error dialog for. Actionable codes
        // (DEVELOPER_ERROR, ITEM_UNAVAILABLE, unknown future codes) keep reporting.
        @Suppress("DEPRECATION")
        internal val IGNORED_LAUNCH_CODES = setOf(
            BillingClient.BillingResponseCode.USER_CANCELED,
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
            BillingClient.BillingResponseCode.ERROR,
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED,
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
            BillingClient.BillingResponseCode.SERVICE_TIMEOUT,
            BillingClient.BillingResponseCode.NETWORK_ERROR,
            BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED,
        )

        internal fun Throwable.tryMapUserFriendly(): Throwable = when {
            this is BillingResultException && this.result.isGplayUnavailableTemporary -> {
                GplayServiceUnavailableException(this)
            }
            this is BillingResultException && this.result.isGplayUnavailablePermanent -> {
                GplayServiceUnavailableException(this)
            }
            this is BillingResultException &&
                this.result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED -> {
                UserCanceledBillingException(this)
            }
            this is BillingResultException &&
                this.result.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                ItemAlreadyOwnedBillingException(this)
            }
            else -> this
        }
    }
}
