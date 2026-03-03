package eu.darken.capod.upgrade.ui

import android.app.Activity
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.SingleEventFlow
import eu.darken.capod.common.uix.ViewModel4
import eu.darken.capod.common.upgrade.core.CapodSku
import eu.darken.capod.common.upgrade.core.UpgradeRepoGplay
import eu.darken.capod.common.upgrade.core.data.BillingDataRepo
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class UpgradeViewModel @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val upgradeRepo: UpgradeRepoGplay,
    private val billingDataRepo: BillingDataRepo,
) : ViewModel4(dispatcherProvider) {

    sealed interface UpgradeEvent {
        data object RestoreFailed : UpgradeEvent
    }

    val events = SingleEventFlow<UpgradeEvent>()

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

    fun startPurchase(activity: Activity) = launch {
        try {
            withContext(dispatcherProvider.Main) {
                billingDataRepo.startIapFlow(activity, CapodSku.PRO_UPGRADE.sku)
            }
        } catch (e: Exception) {
            log(TAG) { "startIapFlow failed: $e" }
            errorEvents.emitBlocking(e)
        }
    }

    fun restorePurchase() = launch {
        try {
            val data = billingDataRepo.getIapData()
            log(TAG) { "Restore check: $data" }
            if (data.purchasedSkus.any { it.sku == CapodSku.PRO_UPGRADE.sku }) {
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
