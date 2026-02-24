package eu.darken.capod.main.ui.settings

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.capod.common.WebpageTool
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.shareLatest
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
        val sponsorUrl: String?,
    )

    val state = upgradeRepo.upgradeInfo
        .map { State(sponsorUrl = upgradeRepo.getSponsorUrl()) }
        .shareLatest(scope = vmScope)

    fun openUrl(url: String) {
        webpageTool.open(url)
    }

    companion object {
        private val TAG = logTag("Settings", "VM")
    }
}
