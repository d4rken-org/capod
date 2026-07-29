package eu.darken.capod.main.ui.settings

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.capod.common.WebpageTool
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.logging.logTag

import eu.darken.capod.common.uix.ViewModel4
import eu.darken.capod.common.upgrade.UpgradeRepo
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val upgradeRepo: UpgradeRepo,
    private val webpageTool: WebpageTool,
) : ViewModel4(dispatcherProvider) {

    data class State(
        val isPro: Boolean,
        val sponsorUrl: String?,
    )

    val state = upgradeRepo.upgradeInfo
        .map {
            State(
                isPro = it.isPro,
                // Only the FOSS flavor has a sponsor flow (its upgrade site IS the sponsor page),
                // on GPlay the entitlement is bought in the app and the heart icon stays hidden.
                sponsorUrl = when (it.type) {
                    UpgradeRepo.Type.FOSS -> upgradeRepo.upgradeSite
                    UpgradeRepo.Type.GPLAY -> null
                },
            )
        }
        .asLiveState()

    fun openUrl(url: String) {
        webpageTool.open(url)
    }

    companion object {
        private val TAG = logTag("Settings", "VM")
    }
}
