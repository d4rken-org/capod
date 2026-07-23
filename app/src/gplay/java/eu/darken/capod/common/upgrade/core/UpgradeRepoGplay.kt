package eu.darken.capod.common.upgrade.core

import android.app.Activity
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase
import eu.darken.capod.common.TimeSource
import eu.darken.capod.common.coroutine.AppScope
import eu.darken.capod.common.debug.logging.Logging.Priority.INFO
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.setupCommonEventHandlers
import eu.darken.capod.common.upgrade.UpgradeRepo
import eu.darken.capod.common.upgrade.core.client.ItemAlreadyOwnedBillingException
import eu.darken.capod.common.upgrade.core.data.BillingData
import eu.darken.capod.common.upgrade.core.data.BillingDataRepo
import eu.darken.capod.common.upgrade.core.data.FreshBillingData
import eu.darken.capod.common.upgrade.core.data.PurchasedSku
import eu.darken.capod.common.upgrade.core.data.Sku
import eu.darken.capod.common.upgrade.core.data.SkuDetails
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import eu.darken.capod.common.datastore.value

@Singleton
class UpgradeRepoGplay @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    private val billingDataRepo: BillingDataRepo,
    private val billingCache: BillingCache,
    private val timeSource: TimeSource,
) : UpgradeRepo {

    // Serializes the sticky check-then-write anchor logic: concurrent fresh observations (init
    // collector, direct restores, failure events) must not interleave between reading the current
    // anchor and stamping the new one.
    private val proStateLock = Mutex()

    // True while the invisible already-owned recovery (the async ITEM_ALREADY_OWNED collector below)
    // is restoring. The ViewModel gates buy actions on it so a buy tap can't race the silent restore
    // and buy the OTHER product on top of what the user already owns (a different-SKU double charge —
    // the ITEM_ALREADY_OWNED reconciliation only covers the same SKU).
    private val autoRestoreBusyState = MutableStateFlow(false)
    val autoRestoreBusy: StateFlow<Boolean> = autoRestoreBusyState.asStateFlow()

    init {
        // Fresh-provenance grace stamping: freshBillingData carries every successful query result
        // and push payload as an event — unlike the equality-deduped billingData state, an
        // unchanged steady-owner query still stamps, and stale listener data can't sneak in.
        // The reactive upgradeInfo mapping deliberately writes nothing anymore.
        billingDataRepo.freshBillingData
            .onEach { fresh ->
                try {
                    recordProState(fresh)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // A failed DataStore write must not kill this process-lifetime collector.
                    log(TAG, WARN) { "Failed to record pro state: ${e.asLog()}" }
                }
            }
            .setupCommonEventHandlers(TAG) { "proStateRecorder" }
            .launchIn(scope)

        // Failed fresh-data attempts (query errors, timeouts) start the unconfirmed-episode clock.
        // Most of these failures are swallowed by their pipelines (logged, retried later), so
        // without this collector a sustained Play outage would never age the grace presentation
        // from "confirming..." into its diagnostics stage.
        billingDataRepo.refreshFailures
            .onEach {
                try {
                    proStateLock.withLock {
                        billingCache.recordProUnconfirmed(timeSource.currentTimeMillis())
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log(TAG, WARN) { "Failed to record unconfirmed state: ${e.asLog()}" }
                }
            }
            .setupCommonEventHandlers(TAG) { "unconfirmedRecorder" }
            .launchIn(scope)

        // Async variant of the launch-result ITEM_ALREADY_OWNED case: Play told us mid-flow that
        // the user already owns it. Reconcile silently — Play shows its own UI for purchase-sheet
        // failures, so no app-side dialog here.
        billingDataRepo.purchaseFailures
            .filter { it.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED }
            .onEach {
                log(TAG, INFO) { "Async already-owned event -> restoring purchase" }
                autoRestoreBusyState.value = true
                try {
                    withTimeoutOrNull(RESTORE_ON_OWNED_TIMEOUT_MS) { restorePurchaseNow() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log(TAG, WARN) { "Async already-owned restore failed: ${e.asLog()}" }
                } finally {
                    autoRestoreBusyState.value = false
                }
            }
            .setupCommonEventHandlers(TAG) { "asyncAlreadyOwned" }
            .launchIn(scope)
    }

    // Grace window depends on what was last owned: a permanent one-time purchase should almost
    // never be dropped on a Play hiccup, so it gets a long window; a subscription legitimately
    // lapses, so it keeps the short one (also used for unknown/legacy last SKUs). Suspend + the
    // cancellable value() read (not valueBlocking's runBlocking) so a hung DataStore read can be
    // cancelled/retried instead of pinning a dispatcher thread.
    private suspend fun graceWindowMs(): Long =
        if (billingCache.lastProStateSku.value().isIapSku()) GRACE_PERIOD_IAP_MS else GRACE_PERIOD_MS

    // Was the last confirmed Pro state within the grace window? Guarded AND bounded: a read failure
    // — the same DataStore a caller may have just failed on — is treated as "not recently Pro"
    // rather than propagating; a hung read is bounded by a timeout so it can't wedge the sequential
    // upgradeInfo mapping and block a later confirmed purchase behind it. Shared by the reactive
    // mapping, the reactive retry and the direct restore so their grace decision can't diverge.
    private suspend fun isRecentlyPro(): Boolean = try {
        val recent = withTimeoutOrNull(GRACE_PROBE_TIMEOUT_MS) {
            (timeSource.currentTimeMillis() - billingCache.lastProStateAt.value()) < graceWindowMs()
        }
        if (recent == null) log(TAG, WARN) { "Grace probe timed out, treating as not-recently-pro" }
        recent ?: false
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log(TAG, WARN) { "Grace probe read failed, treating as not-recently-pro: ${e.asLog()}" }
        false
    }

    // Reactive fallback when the upgradeInfo mapping throws (only local DataStore reads can fail here
    // now — the connection loop retries billing errors itself): keep a recently-Pro user in grace,
    // otherwise surface the error. Never throws — a second cache failure resolves to the error Info.
    private suspend fun graceOrError(error: Throwable): Info =
        if (isRecentlyPro()) Info(gracePeriod = true, billingData = null)
        else Info(billingData = null, error = error)

    private fun String.isIapSku(): Boolean =
        CapodSku.PRO_SKUS.singleOrNull { it.id == this }?.type == Sku.Type.IAP

    // Grace is time-based, but billingData is equality-deduped state kept hot by a
    // process-lifetime subscriber — without this deadline tick, a lapsed grace window would keep
    // isPro=true until the next distinct billing emission or a process restart.
    //
    // Storage-failure resilient: the leading onStart emits immediately so a confirmed purchase in
    // billingData never waits on the first DataStore read (combine can fire, and toUpgradeInfo()'s
    // mapped-first branch surfaces the purchase without touching the cache). The trailing retryWhen
    // catches a failure from EITHER the lastProStateAt.flow source OR graceWindowMs() and keeps the
    // tick alive (emit + capped backoff), so a broken cache can't starve combine or terminate the
    // stream.
    private val graceDeadlineTick: Flow<Unit> = billingCache.lastProStateAt.flow
        .flatMapLatest { lastProAt ->
            flow {
                emit(Unit)
                if (lastProAt > 0L) {
                    val remaining = lastProAt + graceWindowMs() - timeSource.currentTimeMillis()
                    if (remaining > 0) {
                        delay(remaining)
                        emit(Unit)
                    }
                }
            }
        }
        .onStart { emit(Unit) }
        .retryWhen { error, attempt ->
            if (error is CancellationException) return@retryWhen false
            log(TAG, WARN) { "graceDeadlineTick failed (attempt=$attempt): ${error.asLog()}" }
            emit(Unit)
            delay(retryDelayMs(attempt))
            true
        }

    override val upgradeInfo: Flow<UpgradeRepo.Info> = combine(
        billingDataRepo.billingData
            .map<BillingData, BillingData?> { it }
            .onStart { emit(null) },
        graceDeadlineTick,
    ) { data, _ -> data }
        .map { data -> data.toUpgradeInfo() }
        .retryWhen { error, attempt ->
            // Defensive backstop: toUpgradeInfo() now routes its cache access through the
            // guarded+bounded isRecentlyPro() and so never throws for a failing/hung DataStore, and
            // graceDeadlineTick keeps itself alive. This only fires on a genuinely unexpected
            // upstream error — keep the flow alive (a terminal .catch would complete the shared flow
            // and the process-lifetime subscriber would never let it recover) and emit a guarded
            // fallback rather than terminating.
            if (error is CancellationException) return@retryWhen false
            log(TAG, WARN) { "upgradeInfo mapping failed unexpectedly (attempt=$attempt): ${error.asLog()}" }
            emit(graceOrError(error))
            delay(retryDelayMs(attempt))
            true
        }
        .shareIn(scope, SharingStarted.WhileSubscribed(3000L, 0L), replay = 1)

    // True once we've ever confirmed a known Pro purchase on this install; drives the proactive
    // restore banner. Local signal only — a fresh install or switched Google account starts false.
    // Fail-soft: this is combined in the ViewModel OUTSIDE the hardened upgradeInfo, so a DataStore
    // read failure here would otherwise terminate that combine and strand the screen even when
    // upgradeInfo correctly reports Pro. On failure fall back to false and retry.
    val wasEverPro: Flow<Boolean> = billingCache.lastProStateAt.flow
        .map { it > 0 }
        .retryWhen { error, attempt ->
            if (error is CancellationException) return@retryWhen false
            log(TAG, WARN) { "wasEverPro read failed (attempt=$attempt): ${error.asLog()}" }
            emit(false)
            delay(retryDelayMs(attempt))
            true
        }
        .distinctUntilChanged()

    // Start of the current "fresh data can't confirm Pro" episode (0 = none open). Drives the
    // two-stage grace UI: calm confirmation phase first, diagnostics once the episode has aged.
    // Fail-soft for the same reason as wasEverPro: fall back to 0 (no open episode) and retry.
    val proUnconfirmedSince: Flow<Long> = billingCache.proUnconfirmedAt.flow
        .retryWhen { error, attempt ->
            if (error is CancellationException) return@retryWhen false
            log(TAG, WARN) { "proUnconfirmedSince read failed (attempt=$attempt): ${error.asLog()}" }
            emit(0L)
            delay(retryDelayMs(attempt))
            true
        }

    // True once any fresh billing observation arrived this process. The pre-reconciliation empty
    // purchase state must not enable purchase actions — an owner on a fresh install would briefly
    // look free and could buy the other product on top of what they already own.
    val isSettled: Flow<Boolean> = billingDataRepo.freshBillingData
        .map { true }
        .onStart { emit(false) }
        .distinctUntilChanged()

    // Strict SUBS-only ownership check for the switch-to-IAP gate. Errors propagate — a
    // subscriber whose renewal state can't be verified must not be allowed to double-buy.
    suspend fun queryCurrentSubscriptions(): Collection<Purchase> = billingDataRepo.querySubscriptions()

    // Explicit "Restore purchase": query Play now and evaluate Pro from the returned data in the
    // same coroutine (real happens-before), so we never read a stale upgradeInfo replay. Billing
    // errors propagate so the caller can distinguish "not owned" from "Play unavailable".
    suspend fun restorePurchaseNow(): Info {
        log(TAG) { "restorePurchaseNow()" }
        return try {
            val fresh = billingDataRepo.refresh()
            // Returned data is fresh by definition — stamp it even if the flows dedupe the
            // unchanged result and the init collector never sees a new emission. Best-effort: a
            // failed cache write must not turn a successful Play restore into the grace/error path
            // (the mapped info below is returned regardless).
            try {
                recordProState(fresh)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(TAG, WARN) { "restore: failed to record pro state: ${e.asLog()}" }
            }
            fresh.data.toUpgradeInfo()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A transient Play error (or a cache read failure in the mapping) while we were Pro
            // recently keeps us Pro via grace; otherwise surface the error so the caller can show the
            // proper "Play unavailable" message instead of a generic restore failure. isRecentlyPro
            // guards its own probe, so a second failure of the same broken cache resolves to "throw
            // the original error" rather than escaping with the probe's exception.
            if (isRecentlyPro()) {
                log(TAG, VERBOSE) { "Restore hit an error but we were Pro recently -> grace" }
                Info(gracePeriod = true, billingData = null)
            } else {
                throw e
            }
        }
    }

    // Shared Pro/grace mapping used by both the reactive upgradeInfo flow and restorePurchaseNow().
    // Only relinquishes Pro if we haven't had it for a while (grace period). READ-ONLY: this also
    // runs on replayed shared-flow data, so it must never stamp the grace cache — a refunded
    // purchase could otherwise keep re-stamping its own grace window. See recordProState().
    //
    // Branch on MAPPED upgrades before any cache read: a confirmed known purchase is Pro even when
    // local storage is unreadable (mapped-first return, no DataStore access), and a purchase list
    // containing only products this app doesn't know maps to zero upgrades and correctly falls
    // through to the grace check instead of masquerading as a confirmed purchase.
    private suspend fun BillingData?.toUpgradeInfo(): Info {
        val mapped = Info(billingData = this, upgrades = this?.getProSkus() ?: emptyList())
        if (mapped.upgrades.isNotEmpty()) return mapped

        // No confirmed purchase (incl. the null pre-data placeholder the combine seeds): fall back to
        // the grace window via the guarded+bounded probe. Routing through isRecentlyPro() — instead
        // of reading the cache inline — means a failing/hung DataStore can NOT throw out of this
        // mapping. If it could, the map's exception would tear down the flow and the retry would
        // re-inject the null placeholder, looping forever and never processing a later confirmed
        // purchase that arrives behind it in the sequential map.
        return if (isRecentlyPro()) {
            log(TAG, VERBOSE) { "We are not pro, but were recently, did GPlay try annoy us again?" }
            Info(gracePeriod = true, billingData = null)
        } else {
            mapped
        }
    }

    // Persists what fresh data told us about Pro ownership. Callers must only pass FRESH data
    // (returned query results, or new emissions seen by the init collector) — never replayed flow
    // data. A confirmed purchase stamps the anchor and atomically closes any unconfirmed episode;
    // a full snapshot WITHOUT a Pro purchase conclusively failed to confirm and starts the episode
    // clock; presence-only data without a purchase proves nothing either way. The permanent IAP
    // wins as anchor when both are owned, and an IAP anchor is sticky: purchase data may lack the
    // IAP because that query failed or was out of scope (SUBS-only verification), and a
    // subscription seen in the meantime must not shrink the 30d window of an owner whose IAP was
    // never disproven (trade-off: a refunded IAP keeps the long window — consistent with the
    // fail-open cache). Locked: runs concurrently from the init collector, failure events and
    // direct restores, and the sticky check-then-write must not race.
    private suspend fun recordProState(fresh: FreshBillingData) {
        val upgrades = fresh.data.getProSkus()
        val preferred = preferredProSku(upgrades)
        proStateLock.withLock {
            when {
                preferred != null -> {
                    val anchorIsIap = billingCache.lastProStateSku.value().isIapSku()
                    val anchorSku = preferred
                        .takeIf { it.type == Sku.Type.IAP || !anchorIsIap }
                        ?.id
                    billingCache.stampLastProState(anchorSku, timeSource.currentTimeMillis())
                }

                fresh.isFullSnapshot -> {
                    billingCache.recordProUnconfirmed(timeSource.currentTimeMillis())
                }
            }
        }
    }

    data class Info(
        private val gracePeriod: Boolean = false,
        private val billingData: BillingData?,
        val upgrades: Collection<PurchasedSku> = emptyList(),
        override val error: Throwable? = null,
    ) : UpgradeRepo.Info {

        override val type: UpgradeRepo.Type
            get() = UpgradeRepo.Type.GPLAY

        override val isPro: Boolean
            get() = billingData?.getProSku() != null || gracePeriod

        val hasIap: Boolean
            get() = upgrades.any { it.sku is Sku.Iap }

        val hasSub: Boolean
            get() = upgrades.any { it.sku is Sku.Subscription }

        override val upgradedAt: Instant?
            get() = billingData
                ?.getProSku()
                ?.purchase?.purchaseTime
                ?.let { Instant.ofEpochMilli(it) }
    }

    suspend fun querySkus(vararg skus: Sku): Collection<SkuDetails> = billingDataRepo.querySkus(*skus)

    suspend fun launchBillingFlow(
        activity: Activity,
        sku: Sku,
        offer: Sku.Subscription.Offer? = null,
    ) {
        try {
            billingDataRepo.startBillingFlow(activity, sku, offer)
        } catch (e: ItemAlreadyOwnedBillingException) {
            // Stale local state: Play says they already own it, so tapping "buy" really means
            // "unlock what I own" — restore instead of showing an error. Success is silent, the
            // reactive upgradeInfo emission closes the upgrade screen.
            log(TAG, INFO) { "Launch says already owned -> restoring purchase" }
            val restored = try {
                withTimeoutOrNull(RESTORE_ON_OWNED_TIMEOUT_MS) { restorePurchaseNow() }
            } catch (re: CancellationException) {
                throw re
            } catch (re: Exception) {
                log(TAG, WARN) { "Restore after already-owned failed: ${re.asLog()}" }
                null
            }
            // Only the LAUNCHED entitlement showing up explains the already-owned launch failure —
            // a grace-only isPro or a different owned SKU doesn't reconcile it. Fall back to the
            // already-owned dialog with restore tips otherwise.
            val reconciled = restored?.upgrades?.any { it.sku.id == sku.id } == true
            if (!reconciled) throw e
        }
    }

    companion object {
        private fun BillingData.getProSku(): PurchasedSku? = purchasedSkus
            .firstOrNull { it.sku in CapodSku.PRO_SKUS }

        private fun BillingData.getProSkus(): Collection<PurchasedSku> = purchasedSkus
            .filter { it.sku in CapodSku.PRO_SKUS }

        // Keep paying users Pro through transient empty/failed Play Billing responses. A permanent
        // one-time purchase should almost never be dropped on a hiccup, so it gets a long window;
        // a subscription legitimately lapses, so it keeps the short one. GRACE_PERIOD_MS is the
        // subscription/default window (also used when the last-owned SKU is unknown/legacy).
        val GRACE_PERIOD_MS = Duration.ofDays(7).toMillis()
        val GRACE_PERIOD_IAP_MS = Duration.ofDays(30).toMillis()

        // The SKU whose grace window applies when several are owned: the permanent one-time
        // purchase wins over a subscription (purchases are time-sorted, so a plain first() could
        // pick a newer subscription and shrink the window). Null when nothing known is owned.
        internal fun preferredProSku(upgrades: Collection<PurchasedSku>): Sku? =
            upgrades.firstOrNull { it.sku.type == Sku.Type.IAP }?.sku ?: upgrades.firstOrNull()?.sku

        // Backoff for the local-DataStore-failure retries in upgradeInfo and graceDeadlineTick:
        // 30s/60s/120s/240s, capped at 5min. Integer math on purpose — a Double-pow formula could
        // overflow into a hot loop at extreme attempt counts. Pure and unit-tested.
        internal fun retryDelayMs(attempt: Long): Long =
            if (attempt >= 4) 300_000L else 30_000L shl attempt.toInt()

        // Upper bound on a single grace-cache probe: a hung DataStore read resolves to "not
        // recently pro" instead of wedging the sequential upgradeInfo mapping behind it.
        private const val GRACE_PROBE_TIMEOUT_MS = 2_000L

        private const val RESTORE_ON_OWNED_TIMEOUT_MS = 15_000L

        val TAG: String = logTag("Upgrade", "Gplay", "Control")
    }
}
