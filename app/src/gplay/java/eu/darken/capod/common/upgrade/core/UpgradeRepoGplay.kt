package eu.darken.capod.common.upgrade.core

import android.app.Activity
import eu.darken.capod.common.coroutine.AppScope
import eu.darken.capod.common.debug.logging.Logging.Priority.INFO
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.upgrade.UpgradeRepo
import eu.darken.capod.common.upgrade.core.client.ItemAlreadyOwnedBillingException
import eu.darken.capod.common.upgrade.core.data.BillingData
import eu.darken.capod.common.upgrade.core.data.BillingDataRepo
import eu.darken.capod.common.upgrade.core.data.PurchasedSku
import eu.darken.capod.common.upgrade.core.data.Sku
import eu.darken.capod.common.upgrade.core.data.SkuDetails
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import eu.darken.capod.common.datastore.valueBlocking

@Singleton
class UpgradeRepoGplay @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    private val billingDataRepo: BillingDataRepo,
    private val billingCache: BillingCache,
) : UpgradeRepo {

    private var lastProStateAt: Long
        get() = billingCache.lastProStateAt.valueBlocking
        set(value) { billingCache.lastProStateAt.valueBlocking = value }

    private var lastProStateSku: String
        get() = billingCache.lastProStateSku.valueBlocking
        set(value) { billingCache.lastProStateSku.valueBlocking = value }

    private val anchorLock = Any()

    // Grace window depends on what was last owned: a permanent one-time purchase should almost
    // never be dropped on a Play hiccup, so it gets a long window; a subscription legitimately
    // lapses, so it keeps the short one (also used for unknown/legacy last SKUs).
    private fun graceWindowMs(): Long = if (anchorIsIap()) GRACE_PERIOD_IAP_MS else GRACE_PERIOD_MS

    private fun anchorIsIap(): Boolean =
        CapodSku.PRO_SKUS.singleOrNull { it.id == lastProStateSku }?.type == Sku.Type.IAP

    override val upgradeInfo: Flow<UpgradeRepo.Info> = billingDataRepo.billingData
        .map<BillingData, BillingData?> { it }
        .onStart { emit(null) }
        .map { data -> data.toUpgradeInfo() }
        .catch { error ->
            log(TAG, WARN) { "upgradeInfo error: ${error.asLog()}" }
            val now = System.currentTimeMillis()
            if ((now - lastProStateAt) < graceWindowMs()) {
                emit(Info(gracePeriod = true, billingData = null))
            } else {
                emit(Info(billingData = null, error = error))
            }
        }
        .shareIn(scope, SharingStarted.WhileSubscribed(3000L, 0L), replay = 1)

    // True once we've ever confirmed a known Pro purchase on this install; drives the proactive
    // restore banner. Local signal only — a fresh install or switched Google account starts false.
    val wasEverPro: Flow<Boolean> = billingCache.lastProStateAt.flow.map { it > 0 }

    // Explicit "Restore purchase": query Play now and evaluate Pro from the returned data in the
    // same coroutine (real happens-before), so we never read a stale upgradeInfo replay. Billing
    // errors propagate so the caller can distinguish "not owned" from "Play unavailable".
    suspend fun restorePurchaseNow(): Info {
        log(TAG) { "restorePurchaseNow()" }
        return try {
            billingDataRepo.refresh().toUpgradeInfo()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Mirror the reactive flow's catch: a transient Play error while we were Pro recently
            // keeps us Pro via the grace period; otherwise surface the error so the caller can show
            // the proper "Play unavailable" message instead of a generic restore failure.
            if ((System.currentTimeMillis() - lastProStateAt) < graceWindowMs()) {
                log(TAG, VERBOSE) { "Restore hit a Play error but we were Pro recently -> grace" }
                Info(gracePeriod = true, billingData = null)
            } else {
                throw e
            }
        }
    }

    // Shared Pro/grace mapping used by both the reactive upgradeInfo flow and restorePurchaseNow().
    // Only relinquishes Pro if we haven't had it for a while (grace period).
    private fun BillingData?.toUpgradeInfo(): Info {
        val now = System.currentTimeMillis()
        val proSku = this?.getProSku()
        log(TAG) { "toUpgradeInfo(): now=$now, lastProStateAt=$lastProStateAt, data=$this" }
        return when {
            proSku != null -> {
                val upgrades = this!!.getProSkus()
                // Record which Pro SKU anchors the grace window; the permanent IAP wins when both
                // are owned. An IAP anchor is sticky: purchase data may lack the IAP because that
                // query failed on a fresh connection, and a subscription seen in the meantime must
                // not shrink the 30d window of an owner whose IAP was never disproven. Trade-off:
                // a refunded IAP keeps the long window — consistent with the fail-open cache.
                // SKU first, timestamp last — the timestamp is the gate, so a crash between the
                // two writes stays conservative. Locked: mappings run concurrently from the hot
                // reactive flow and direct restores, and the sticky check-then-write must not race.
                synchronized(anchorLock) {
                    preferredProSku(upgrades)
                        ?.takeIf { it.type == Sku.Type.IAP || !anchorIsIap() }
                        ?.let { lastProStateSku = it.id }
                    lastProStateAt = now
                }
                Info(billingData = this, upgrades = upgrades)
            }

            (now - lastProStateAt) < graceWindowMs() -> {
                log(TAG, VERBOSE) { "We are not pro, but were recently, did GPlay try annoy us again?" }
                Info(gracePeriod = true, billingData = null)
            }

            else -> Info(billingData = this, upgrades = this?.getProSkus() ?: emptyList())
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
            if (restored?.isPro != true) {
                // Couldn't reconcile the entitlement (pending purchase, account mismatch, Play
                // quirk) — fall back to the already-owned dialog with restore tips.
                throw e
            }
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
