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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import eu.darken.capod.common.datastore.value
import eu.darken.capod.common.datastore.valueBlocking

@Singleton
class UpgradeRepoGplay @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    private val billingDataRepo: BillingDataRepo,
    private val billingCache: BillingCache,
    private val timeSource: TimeSource,
) : UpgradeRepo {

    private val lastProStateAt: Long
        get() = billingCache.lastProStateAt.valueBlocking

    // Serializes the sticky check-then-write anchor logic: concurrent fresh observations (init
    // collector, direct restores, failure events) must not interleave between reading the current
    // anchor and stamping the new one.
    private val proStateLock = Mutex()

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
                try {
                    withTimeoutOrNull(RESTORE_ON_OWNED_TIMEOUT_MS) { restorePurchaseNow() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log(TAG, WARN) { "Async already-owned restore failed: ${e.asLog()}" }
                }
            }
            .setupCommonEventHandlers(TAG) { "asyncAlreadyOwned" }
            .launchIn(scope)
    }

    // Grace window depends on what was last owned: a permanent one-time purchase should almost
    // never be dropped on a Play hiccup, so it gets a long window; a subscription legitimately
    // lapses, so it keeps the short one (also used for unknown/legacy last SKUs).
    private fun graceWindowMs(): Long =
        if (billingCache.lastProStateSku.valueBlocking.isIapSku()) GRACE_PERIOD_IAP_MS else GRACE_PERIOD_MS

    private fun String.isIapSku(): Boolean =
        CapodSku.PRO_SKUS.singleOrNull { it.id == this }?.type == Sku.Type.IAP

    // Grace is time-based, but billingData is equality-deduped state kept hot by a
    // process-lifetime subscriber — without this deadline tick, a lapsed grace window would keep
    // isPro=true until the next distinct billing emission or a process restart.
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

    override val upgradeInfo: Flow<UpgradeRepo.Info> = combine(
        billingDataRepo.billingData
            .map<BillingData, BillingData?> { it }
            .onStart { emit(null) },
        graceDeadlineTick,
    ) { data, _ -> data }
        .map { data -> data.toUpgradeInfo() }
        .catch { error ->
            log(TAG, WARN) { "upgradeInfo error: ${error.asLog()}" }
            val now = timeSource.currentTimeMillis()
            if ((now - lastProStateAt) < graceWindowMs()) {
                emit(Info(gracePeriod = true, billingData = null))
            } else {
                emit(Info(billingData = null, error = error))
            }
        }
        .shareIn(scope, SharingStarted.WhileSubscribed(3000L, 0L), replay = 1)

    // True once we've ever confirmed a known Pro purchase on this install; drives the proactive
    // restore banner. Local signal only — a fresh install or switched Google account starts false.
    val wasEverPro: Flow<Boolean> = billingCache.lastProStateAt.flow
        .map { it > 0 }
        .distinctUntilChanged()

    // Start of the current "fresh data can't confirm Pro" episode (0 = none open). Drives the
    // two-stage grace UI: calm confirmation phase first, diagnostics once the episode has aged.
    val proUnconfirmedSince: Flow<Long> = billingCache.proUnconfirmedAt.flow

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
            // unchanged result and the init collector never sees a new emission.
            recordProState(fresh)
            fresh.data.toUpgradeInfo()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Mirror the reactive flow's catch: a transient Play error while we were Pro recently
            // keeps us Pro via the grace period; otherwise surface the error so the caller can show
            // the proper "Play unavailable" message instead of a generic restore failure.
            if ((timeSource.currentTimeMillis() - lastProStateAt) < graceWindowMs()) {
                log(TAG, VERBOSE) { "Restore hit a Play error but we were Pro recently -> grace" }
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
    private fun BillingData?.toUpgradeInfo(): Info {
        val now = timeSource.currentTimeMillis()
        val proSku = this?.getProSku()
        log(TAG) { "toUpgradeInfo(): now=$now, lastProStateAt=$lastProStateAt, data=$this" }
        return when {
            proSku != null -> Info(billingData = this, upgrades = this!!.getProSkus())

            (now - lastProStateAt) < graceWindowMs() -> {
                log(TAG, VERBOSE) { "We are not pro, but were recently, did GPlay try annoy us again?" }
                Info(gracePeriod = true, billingData = null)
            }

            else -> Info(billingData = this, upgrades = this?.getProSkus() ?: emptyList())
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

        private const val RESTORE_ON_OWNED_TIMEOUT_MS = 15_000L

        val TAG: String = logTag("Upgrade", "Gplay", "Control")
    }
}
