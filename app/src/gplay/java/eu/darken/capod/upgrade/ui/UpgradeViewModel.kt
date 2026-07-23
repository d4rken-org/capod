package eu.darken.capod.upgrade.ui

import android.app.Activity
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.capod.common.BuildConfigWrap
import eu.darken.capod.common.TimeSource
import eu.darken.capod.common.WebpageTool
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.logging.Logging.Priority.INFO
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.SingleEventFlow
import eu.darken.capod.common.uix.ViewModel4
import eu.darken.capod.common.upgrade.core.CapodSku
import eu.darken.capod.common.upgrade.core.UpgradeRepoGplay
import eu.darken.capod.common.upgrade.core.client.UserCanceledBillingException
import eu.darken.capod.common.upgrade.core.data.Sku
import eu.darken.capod.common.upgrade.core.data.SkuDetails
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import javax.inject.Inject

@HiltViewModel
class UpgradeViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val upgradeRepo: UpgradeRepoGplay,
    private val webpageTool: WebpageTool,
    private val timeSource: TimeSource,
) : ViewModel4(dispatcherProvider) {

    sealed interface UpgradeEvent {
        data object RestoreFailed : UpgradeEvent
        data object RestoreSucceeded : UpgradeEvent
        data object SubscriptionStillRenewing : UpgradeEvent
        data object SubscriptionCheckFailed : UpgradeEvent
    }

    val events = SingleEventFlow<UpgradeEvent>()

    private val restoring = MutableStateFlow(false)

    // Single-flight guard for ALL purchase actions, held from tap until the Play sheet launch
    // resolved — the disabled buttons are best-effort (recomposition lags), this is authoritative.
    private val purchaseBusy = MutableStateFlow(false)

    // Route binding: null until the host reports whether this is the manage route. The auto-close
    // collector waits for it, so a manage visit can never race a premature navUp().
    private val manageRoute = MutableStateFlow<Boolean?>(null)

    fun initialize(manage: Boolean) {
        if (manageRoute.value == null) {
            log(TAG) { "initialize(manage=$manage)" }
            manageRoute.value = manage
        }
    }

    // Purchase actions stay disabled until the first billing reconciliation of this process (or a
    // bounded fallback so a Play outage can't brick the buttons): the initially-empty purchase
    // state must not let an owner on a fresh install double-buy.
    private val settled: StateFlow<Boolean> = merge(
        upgradeRepo.isSettled.filter { it },
        flow {
            delay(SETTLE_FALLBACK_MS)
            emit(true)
        },
    ).stateIn(vmScope, SharingStarted.Eagerly, false)

    // Bumped by retrySkuQuery() to re-run the aggregate query after a cold/slow-Play failure left
    // the offers unavailable — without it the Lazily-cached failure would brick offer selection for
    // the whole ViewModel lifetime (only leaving and reopening the screen recovered).
    private val retryTrigger = MutableStateFlow(0)

    // One aggregate SKU-detail query per retry generation, both types concurrently. Failures
    // resolve to null details — owners/grace render price-independently, acquisition users get
    // the fallback purchase UI.
    private val skuQueries = retryTrigger.flatMapLatest {
        flow {
            emit(SkuQueryState())
            val result = coroutineScope {
                val iap = async { querySkuDetailsSafe(CapodSku.Iap.PRO_UPGRADE) }
                val sub = async { querySkuDetailsSafe(CapodSku.Sub.PRO_UPGRADE) }
                SkuQueryState(done = true, iap = iap.await(), sub = sub.await())
            }
            emit(result)
        }
    }.shareIn(vmScope, SharingStarted.Lazily, replay = 1)

    private suspend fun querySkuDetailsSafe(sku: Sku): SkuDetails? = try {
        withTimeoutOrNull(SKU_QUERY_TIMEOUT_MS) {
            upgradeRepo.querySkus(sku).firstOrNull()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log(TAG, WARN) { "Failed to query SKU ${sku.id}: ${e.asLog()}" }
        null
    }

    // Re-runs the SKU queries from the fallback "Retry" affordance. The button that calls this is
    // disabled while a query is in flight (skuQueryInProgress), which is the actual thrash guard for
    // owners/grace users who keep the fallback visible price-independently; a re-trigger that still
    // slips through only cancels-and-restarts the flatMapLatest query (latest wins, each attempt is
    // bounded by SKU_QUERY_TIMEOUT_MS), so it can't leak or wedge.
    fun retrySkuQuery() {
        log(TAG) { "retrySkuQuery()" }
        retryTrigger.update { it + 1 }
    }

    // Manual restore OR the repo's invisible already-owned recovery — either one pauses the buy
    // actions, so the two can't be raced against each other from the UI.
    private val effectiveRestore: Flow<Boolean> = combine(
        restoring,
        upgradeRepo.autoRestoreBusy,
    ) { manual, auto -> manual || auto }

    // Re-evaluates the grace presentation when the open episode crosses the diagnostics
    // threshold — every other combined flow is distinct-until-changed and would never re-fire.
    private val graceTick = upgradeRepo.proUnconfirmedSince
        .flatMapLatest { stamp ->
            flow {
                emit(Unit)
                if (stamp > 0L) {
                    val remaining = stamp + GRACE_DIAGNOSTICS_AFTER_MS - timeSource.currentTimeMillis()
                    if (remaining > 0) {
                        delay(remaining)
                        emit(Unit)
                    }
                }
            }
        }

    private data class BillingState(
        val info: UpgradeRepoGplay.Info?,
        val wasEverPro: Boolean,
        val proUnconfirmedSince: Long,
    )

    private val billingState = combine(
        upgradeRepo.upgradeInfo,
        upgradeRepo.wasEverPro,
        upgradeRepo.proUnconfirmedSince,
        graceTick,
    ) { info, wasEverPro, unconfirmedSince, _ ->
        BillingState(
            info = info as? UpgradeRepoGplay.Info,
            wasEverPro = wasEverPro,
            proUnconfirmedSince = unconfirmedSince,
        )
    }

    val state: StateFlow<UpgradeUiState> = combine(
        billingState,
        skuQueries,
        settled,
        effectiveRestore,
        purchaseBusy,
    ) { billing, skus, isSettled, isRestoring, isBusy ->
        val info = billing.info
        val ownership = info?.toOwnership() ?: Ownership()
        val grace = if (info?.isPro == true && !ownership.ownsAnything) {
            GraceHint(
                showDiagnostics = billing.proUnconfirmedSince > 0L &&
                    timeSource.currentTimeMillis() - billing.proUnconfirmedSince >= GRACE_DIAGNOSTICS_AFTER_MS
            )
        } else {
            null
        }

        // Owners and grace users render price-independently: their status view must not degrade
        // to a spinner (or an error) just because the SKU pricing queries failed or are slow.
        val priceIndependent = ownership.ownsAnything || grace != null
        if (!priceIndependent && !skus.done) {
            UpgradeUiState.Loading
        } else {
            toLoadedState(
                skus = skus,
                ownership = ownership,
                grace = grace,
                // Hidden while a grace period or an actual purchase keeps the user Pro.
                showRestoreBanner = billing.wasEverPro && info?.isPro != true,
                settled = isSettled,
                restoreInProgress = isRestoring,
                verificationInProgress = isBusy,
                // Owners/grace users keep the fallback + Retry visible while a query is still
                // running; disable Retry then so repeated taps can't thrash the query flow.
                skuQueryInProgress = !skus.done,
            )
        }
    }.stateIn(vmScope, SharingStarted.WhileSubscribed(5_000), UpgradeUiState.Loading)

    init {
        // Sales route: close once the user is Pro (purchase completed, or they were Pro all
        // along). Manage route: never auto-close — it exists to LOOK at the status.
        manageRoute
            .filterNotNull()
            .flatMapLatest { manage ->
                if (manage) emptyFlow() else upgradeRepo.upgradeInfo
            }
            .filter { it.isPro }
            .take(1)
            .onEach {
                log(TAG) { "User is pro on the sales route, navigating back" }
                navUp()
            }
            .launchIn(vmScope)
    }

    fun onGoIap(activity: Activity) {
        log(TAG, INFO) { "onGoIap()" }
        launch {
            runExclusive {
                // ALWAYS verify against a fresh SUBS-only query, not just for known subscribers:
                // the replayed ownership state can be stale or still empty right after process
                // start, and a renewing subscriber must never double-buy. Fails closed.
                val subscriptions = try {
                    withTimeoutOrNull(VERIFY_TIMEOUT_MS) { upgradeRepo.queryCurrentSubscriptions() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log(TAG, WARN) { "Subscription verification failed: ${e.asLog()}" }
                    errorEvents.emitBlocking(e)
                    return@runExclusive
                }
                when {
                    subscriptions == null -> {
                        log(TAG, WARN) { "Subscription verification timed out" }
                        events.tryEmit(UpgradeEvent.SubscriptionCheckFailed)
                    }

                    subscriptions.any { it.isAutoRenewing } -> {
                        log(TAG, INFO) { "Subscription still set to renew -> blocking IAP purchase" }
                        events.tryEmit(UpgradeEvent.SubscriptionStillRenewing)
                    }

                    else -> launchBillingFlow(activity, CapodSku.Iap.PRO_UPGRADE, null)
                }
            }
        }
    }

    fun onGoSubscription(activity: Activity) {
        log(TAG, INFO) { "onGoSubscription()" }
        launch {
            runExclusive { launchBillingFlow(activity, CapodSku.Sub.PRO_UPGRADE, CapodSku.Sub.PRO_UPGRADE.BASE_OFFER) }
        }
    }

    fun onGoSubscriptionTrial(activity: Activity) {
        log(TAG, INFO) { "onGoSubscriptionTrial()" }
        launch {
            runExclusive { launchBillingFlow(activity, CapodSku.Sub.PRO_UPGRADE, CapodSku.Sub.PRO_UPGRADE.TRIAL_OFFER) }
        }
    }

    // Single-flight for purchase actions: the guard is held from the tap until the Play sheet
    // launch has resolved, so repeated taps can't stack verification queries or billing flows.
    private suspend fun runExclusive(block: suspend () -> Unit) {
        // Authoritative gate for the invisible already-owned recovery: button disabling is
        // best-effort (recomposition lags a tap), so a subscribe/buy tap dispatched while the silent
        // restore runs must be refused here, or it could buy the OTHER product on top of what the
        // user already owns (a different-SKU double charge the ITEM_ALREADY_OWNED path won't catch).
        if (upgradeRepo.autoRestoreBusy.value) {
            log(TAG) { "Purchase action ignored, auto-restore in progress" }
            return
        }
        if (restoring.value) {
            log(TAG) { "Purchase action ignored, restore in progress" }
            return
        }
        if (!purchaseBusy.compareAndSet(expect = false, update = true)) {
            log(TAG) { "Purchase action ignored, another one is in flight" }
            return
        }
        try {
            block()
        } finally {
            purchaseBusy.value = false
        }
    }

    private suspend fun launchBillingFlow(activity: Activity, sku: Sku, offer: Sku.Subscription.Offer?) {
        try {
            upgradeRepo.launchBillingFlow(activity, sku, offer)
        } catch (e: CancellationException) {
            throw e
        } catch (e: UserCanceledBillingException) {
            // Backing out of the payment sheet is a normal user action, not an error.
            log(TAG) { "User canceled the billing flow" }
        } catch (e: Exception) {
            log(TAG, WARN) { "launchBillingFlow(${sku.id}) failed: ${e.asLog()}" }
            errorEvents.emitBlocking(e)
        }
    }

    fun restorePurchase() = launch {
        // Don't overlap the invisible already-owned recovery — it is itself a restore.
        if (upgradeRepo.autoRestoreBusy.value) {
            log(TAG) { "restorePurchase() ignored, auto-restore in progress" }
            return@launch
        }
        // Symmetric to runExclusive: a restore must not overlap an in-flight verification or
        // billing launch either, or the user could end up with two result dialogs stacked.
        if (purchaseBusy.value) {
            log(TAG) { "restorePurchase() ignored, purchase action in flight" }
            return@launch
        }
        // Single-flight: repeated taps while a restore is running (worst case bounded by
        // RESTORE_TIMEOUT_MS) must not stack concurrent restores and duplicate result messages.
        if (!restoring.compareAndSet(expect = false, update = true)) {
            log(TAG) { "restorePurchase() ignored, already in progress" }
            return@launch
        }
        log(TAG, INFO) { "restorePurchase()" }

        try {
            // Pad the round-trip to a minimum visible duration, CONCURRENTLY with the real query
            // (a pad, not an add-on): warm caches can answer instantly, and a spinner that flashes
            // for a single frame leaves the user unsure whether anything actually happened.
            val restored = coroutineScope {
                val minVisible = async { delay(RESTORE_MIN_VISIBLE_MS) }
                val result = withTimeoutOrNull(RESTORE_TIMEOUT_MS) { upgradeRepo.restorePurchaseNow() }
                minVisible.await()
                result
            }
            when {
                restored == null -> {
                    // Play never answered in time; the restore-failed message already suggests the
                    // purchase may take a while to sync, which fits a timeout too.
                    log(TAG, WARN) { "Restore purchase timed out" }
                    events.tryEmit(UpgradeEvent.RestoreFailed)
                }

                // An actual returned purchase is required — a grace-only isPro means Play still
                // couldn't confirm anything, which is not a successful restore.
                restored.upgrades.isNotEmpty() -> {
                    log(TAG, INFO) { "Restored purchase :))" }
                    events.tryEmit(UpgradeEvent.RestoreSucceeded)
                }

                else -> {
                    log(TAG, WARN) { "No pro purchase found" }
                    events.tryEmit(UpgradeEvent.RestoreFailed)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Play/billing error (e.g. service unavailable): surface the proper error dialog
            // instead of the generic "restore failed" toast, so the user can tell the cases apart.
            log(TAG, WARN) { "Restore purchase errored: ${e.asLog()}" }
            errorEvents.emitBlocking(e)
        } finally {
            // Reset only after result handling, so the single-flight guard covers the whole action.
            restoring.value = false
        }
    }

    fun onManageSubscription() {
        log(TAG, INFO) { "onManageSubscription()" }
        webpageTool.open(PLAY_SUBSCRIPTION_URL)
    }

    fun onResume() {
        // Returning from Play (e.g. after cancelling renewal on the Manage page) must reflect the
        // new renewal state promptly — the global foreground refresh is throttled to once an
        // hour, which would leave the switch offer locked long after the user cancelled.
        val current = state.value
        val hasSub = (current as? UpgradeUiState.Loaded)?.ownership?.subscription != null
        if (!hasSub) return
        launch {
            try {
                withTimeoutOrNull(VERIFY_TIMEOUT_MS) { upgradeRepo.queryCurrentSubscriptions() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(TAG, WARN) { "Resume subscription refresh failed: ${e.asLog()}" }
            }
        }
    }

    companion object {
        internal const val RESTORE_TIMEOUT_MS = 15_000L
        // Long enough that the user believes a round-trip to Play happened, short enough not
        // to drag.
        internal const val RESTORE_MIN_VISIBLE_MS = 1_500L
        internal const val VERIFY_TIMEOUT_MS = 10_000L
        // The first SKU query after a Play sign-in has been observed to take >8s.
        internal const val SKU_QUERY_TIMEOUT_MS = 15_000L
        internal const val SETTLE_FALLBACK_MS = 10_000L
        internal val GRACE_DIAGNOSTICS_AFTER_MS = Duration.ofHours(24).toMillis()
        internal val PLAY_SUBSCRIPTION_URL =
            "https://play.google.com/store/account/subscriptions" +
                "?sku=${CapodSku.Sub.PRO_UPGRADE.id}&package=${BuildConfigWrap.APPLICATION_ID}"
        private val TAG = logTag("Upgrade", "VM")
    }
}
