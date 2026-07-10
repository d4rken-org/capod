package eu.darken.capod.upgrade.ui

import android.app.Activity
import dagger.hilt.android.lifecycle.HiltViewModel
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
import eu.darken.capod.common.upgrade.core.data.SkuDetails
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class UpgradeViewModel @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val upgradeRepo: UpgradeRepoGplay,
) : ViewModel4(dispatcherProvider) {

    sealed interface UpgradeEvent {
        data object RestoreFailed : UpgradeEvent
    }

    sealed interface BillingEvent {
        data object LaunchIap : BillingEvent
        data object LaunchSubscription : BillingEvent
        data object LaunchSubscriptionTrial : BillingEvent
    }

    data class Pricing(
        val iap: SkuDetails? = null,
        val sub: SkuDetails? = null,
        val hasIap: Boolean = false,
        val hasSub: Boolean = false,
        val subPrice: String? = null,
        val iapPrice: String? = null,
        val hasTrialOffer: Boolean = false,
    ) {
        val subAvailable: Boolean get() = sub != null || subPrice != null
        val iapAvailable: Boolean get() = iap != null || iapPrice != null
    }

    val events = SingleEventFlow<UpgradeEvent>()
    val billingEvents = SingleEventFlow<BillingEvent>()

    val state: StateFlow<Pricing?> = flow {
        val iapDetails = try {
            withTimeoutOrNull(5_000L) {
                upgradeRepo.querySkus(CapodSku.Iap.PRO_UPGRADE).firstOrNull()
            }
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to query IAP SKU: ${e.asLog()}" }
            null
        }

        val subDetails = try {
            withTimeoutOrNull(5_000L) {
                upgradeRepo.querySkus(CapodSku.Sub.PRO_UPGRADE).firstOrNull()
            }
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to query Sub SKU: ${e.asLog()}" }
            null
        }

        val info = try {
            withTimeoutOrNull(5_000L) {
                upgradeRepo.upgradeInfo.first() as? UpgradeRepoGplay.Info
            }
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to get upgrade info: ${e.asLog()}" }
            null
        }

        val subProductDetails = subDetails?.details
        val baseOffer = subProductDetails?.subscriptionOfferDetails?.firstOrNull { offerDetails ->
            CapodSku.Sub.PRO_UPGRADE.BASE_OFFER.matches(offerDetails)
        }
        val hasTrialOffer = subProductDetails?.subscriptionOfferDetails?.any { offerDetails ->
            CapodSku.Sub.PRO_UPGRADE.TRIAL_OFFER.matches(offerDetails)
        } == true

        emit(
            Pricing(
                iap = iapDetails,
                sub = subDetails,
                hasIap = info?.hasIap == true,
                hasSub = info?.hasSub == true,
                subPrice = baseOffer?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice,
                iapPrice = iapDetails?.details?.oneTimePurchaseOfferDetails?.formattedPrice,
                hasTrialOffer = hasTrialOffer,
            )
        )
    }.stateIn(vmScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        upgradeRepo.upgradeInfo
            .onEach { info ->
                if (info.isPro) {
                    log(TAG) { "User is now pro, navigating back" }
                    navUp()
                }
            }
            .launchIn(vmScope)
    }

    fun onGoIap() {
        log(TAG, INFO) { "onGoIap()" }
        billingEvents.tryEmit(BillingEvent.LaunchIap)
    }

    fun onGoSubscription() {
        log(TAG, INFO) { "onGoSubscription()" }
        billingEvents.tryEmit(BillingEvent.LaunchSubscription)
    }

    fun onGoSubscriptionTrial() {
        log(TAG, INFO) { "onGoSubscriptionTrial()" }
        billingEvents.tryEmit(BillingEvent.LaunchSubscriptionTrial)
    }

    fun launchBillingIap(activity: Activity) = launch {
        log(TAG, INFO) { "launchBillingIap()" }
        try {
            withContext(dispatcherProvider.Main) {
                upgradeRepo.launchBillingFlow(activity, CapodSku.Iap.PRO_UPGRADE)
            }
        } catch (e: Exception) {
            log(TAG) { "launchBillingIap failed: $e" }
            errorEvents.emitBlocking(e)
        }
    }

    fun launchBillingSubscription(activity: Activity) = launch {
        log(TAG, INFO) { "launchBillingSubscription()" }
        try {
            withContext(dispatcherProvider.Main) {
                upgradeRepo.launchBillingFlow(
                    activity,
                    CapodSku.Sub.PRO_UPGRADE,
                    CapodSku.Sub.PRO_UPGRADE.BASE_OFFER,
                )
            }
        } catch (e: Exception) {
            log(TAG) { "launchBillingSubscription failed: $e" }
            errorEvents.emitBlocking(e)
        }
    }

    fun launchBillingSubscriptionTrial(activity: Activity) = launch {
        log(TAG, INFO) { "launchBillingSubscriptionTrial()" }
        try {
            withContext(dispatcherProvider.Main) {
                upgradeRepo.launchBillingFlow(
                    activity,
                    CapodSku.Sub.PRO_UPGRADE,
                    CapodSku.Sub.PRO_UPGRADE.TRIAL_OFFER,
                )
            }
        } catch (e: Exception) {
            log(TAG) { "launchBillingSubscriptionTrial failed: $e" }
            errorEvents.emitBlocking(e)
        }
    }

    fun restorePurchase() = launch {
        log(TAG, INFO) { "restorePurchase()" }

        val restored = try {
            withTimeoutOrNull(RESTORE_TIMEOUT_MS) { upgradeRepo.restorePurchaseNow() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Play/billing error (e.g. service unavailable): surface the proper error dialog
            // instead of the generic "restore failed" toast, so the user can tell the cases apart.
            log(TAG, WARN) { "Restore purchase errored: ${e.asLog()}" }
            errorEvents.emitBlocking(e)
            return@launch
        }

        when {
            restored == null -> {
                // Play never answered in time; the restore-failed message already suggests the
                // purchase may take a while to sync, which fits a timeout too.
                log(TAG, WARN) { "Restore purchase timed out" }
                events.tryEmit(UpgradeEvent.RestoreFailed)
            }

            restored.isPro -> log(TAG, INFO) { "Restored purchase :))" }

            else -> {
                log(TAG, WARN) { "No pro purchase found" }
                events.tryEmit(UpgradeEvent.RestoreFailed)
            }
        }
    }

    companion object {
        internal const val RESTORE_TIMEOUT_MS = 15_000L
        private val TAG = logTag("Upgrade", "VM")
    }
}
