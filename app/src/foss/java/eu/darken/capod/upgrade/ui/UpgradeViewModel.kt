package eu.darken.capod.upgrade.ui

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.capod.common.WebpageTool
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.uix.ViewModel4
import eu.darken.capod.common.upgrade.core.FossUpgrade
import eu.darken.capod.common.upgrade.core.UpgradeControlFoss
import javax.inject.Inject

@HiltViewModel
class UpgradeViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val upgradeControlFoss: UpgradeControlFoss,
    private val webpageTool: WebpageTool,
) : ViewModel4(dispatcherProvider) {

    fun sponsor() {
        upgradeControlFoss.upgrade(FossUpgrade.Reason.DONATED)
        webpageTool.open("https://github.com/sponsors/d4rken")
        navUp()
    }
}
