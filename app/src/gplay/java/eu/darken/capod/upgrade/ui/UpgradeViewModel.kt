package eu.darken.capod.upgrade.ui

import android.app.Activity
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.SingleEventFlow
import eu.darken.capod.common.uix.ViewModel4
import eu.darken.capod.common.upgrade.core.CapodSku
import eu.darken.capod.common.upgrade.core.UpgradeRepoGplay
import eu.darken.capod.common.upgrade.core.data.SkuDetails
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
        billingEvents.tryEmit(BillingEvent.LaunchIap)
    }

    fun onGoSubscription() {
        billingEvents.tryEmit(BillingEvent.LaunchSubscription)
    }

    fun onGoSubscriptionTrial() {
        billingEvents.tryEmit(BillingEvent.LaunchSubscriptionTrial)
    }

    fun launchBillingIap(activity: Activity) = launch {
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
        try {
            val data = upgradeRepo.refresh()
            log(TAG) { "Restore check: $data" }
            val info = upgradeRepo.upgradeInfo.first()
            if (info.isPro) {
                log(TAG) { "Pro purchase found" }
            } else {
                log(TAG) { "No pro purchase found" }
                events.tryEmit(UpgradeEvent.RestoreFailed)
            }
        } catch (e: Exception) {
            log(TAG) { "Restore failed: $e" }
            errorEvents.emitBlocking(e)
        }
    }

    companion object {
        private val TAG = logTag("Upgrade", "VM")
    }
}
