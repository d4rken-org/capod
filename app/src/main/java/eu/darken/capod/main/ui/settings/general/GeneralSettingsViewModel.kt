package eu.darken.capod.main.ui.settings.general

import android.app.Activity
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.capod.common.bluetooth.ScannerMode
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.SingleEventFlow
import eu.darken.capod.common.flow.shareLatest
import eu.darken.capod.common.navigation.Nav
import eu.darken.capod.common.theming.ThemeColor
import eu.darken.capod.common.theming.ThemeMode
import eu.darken.capod.common.theming.ThemeState
import eu.darken.capod.common.theming.ThemeStyle
import eu.darken.capod.common.uix.ViewModel4
import eu.darken.capod.common.upgrade.UpgradeRepo
import eu.darken.capod.main.core.GeneralSettings
import eu.darken.capod.main.core.MonitorMode
import eu.darken.capod.main.core.themeState
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class GeneralSettingsViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val generalSettings: GeneralSettings,
    private val upgradeRepo: UpgradeRepo,
) : ViewModel4(dispatcherProvider) {

    data class State(
        val isPro: Boolean,
        val monitorMode: MonitorMode,
        val scannerMode: ScannerMode,
        val showConnectedNotification: Boolean,
        val keepNotificationAfterDisconnect: Boolean,
        val isOffloadedFilteringDisabled: Boolean,
        val isOffloadedBatchingDisabled: Boolean,
        val useIndirectScanResultCallback: Boolean,
        val themeState: ThemeState,
    )

    private val isPro = upgradeRepo.upgradeInfo.map { it.isPro }.shareLatest(scope = vmScope)

    val launchUpgradeFlow = SingleEventFlow<(Activity) -> Unit>()

    val state = combine(
        combine(
            generalSettings.monitorMode.flow,
            generalSettings.scannerMode.flow,
            generalSettings.useExtraMonitorNotification.flow,
            generalSettings.keepConnectedNotificationAfterDisconnect.flow,
        ) { monitorMode, scannerMode, showNotif, keepNotif ->
            @Suppress("USELESS_CAST")
            arrayOf<Any>(monitorMode as Any, scannerMode as Any, showNotif as Any, keepNotif as Any)
        },
        combine(
            generalSettings.isOffloadedFilteringDisabled.flow,
            generalSettings.isOffloadedBatchingDisabled.flow,
            generalSettings.useIndirectScanResultCallback.flow,
        ) { filtering, batching, indirect ->
            @Suppress("USELESS_CAST")
            arrayOf<Any>(filtering as Any, batching as Any, indirect as Any)
        },
        generalSettings.themeState,
        isPro,
    ) { general, compat, themeState, isPro ->
        State(
            isPro = isPro,
            monitorMode = general[0] as MonitorMode,
            scannerMode = general[1] as ScannerMode,
            showConnectedNotification = general[2] as Boolean,
            keepNotificationAfterDisconnect = general[3] as Boolean,
            isOffloadedFilteringDisabled = compat[0] as Boolean,
            isOffloadedBatchingDisabled = compat[1] as Boolean,
            useIndirectScanResultCallback = compat[2] as Boolean,
            themeState = themeState,
        )
    }.shareLatest(scope = vmScope)

    fun setMonitorMode(mode: MonitorMode) {
        generalSettings.monitorMode.value = mode
    }

    fun setScannerMode(mode: ScannerMode) {
        generalSettings.scannerMode.value = mode
    }

    fun setShowConnectedNotification(enabled: Boolean) {
        generalSettings.useExtraMonitorNotification.value = enabled
    }

    fun setKeepNotificationAfterDisconnect(enabled: Boolean) {
        generalSettings.keepConnectedNotificationAfterDisconnect.value = enabled
    }

    fun setOffloadedFilteringDisabled(disabled: Boolean) {
        generalSettings.isOffloadedFilteringDisabled.value = disabled
    }

    fun setOffloadedBatchingDisabled(disabled: Boolean) {
        generalSettings.isOffloadedBatchingDisabled.value = disabled
    }

    fun setUseIndirectScanResultCallback(enabled: Boolean) {
        generalSettings.useIndirectScanResultCallback.value = enabled
    }

    fun setThemeMode(mode: ThemeMode) = launch {
        if (isPro.first()) {
            generalSettings.themeMode.value = mode
        } else {
            launchUpgradeFlow.tryEmit { upgradeRepo.launchBillingFlow(it) }
        }
    }

    fun setThemeStyle(style: ThemeStyle) = launch {
        if (isPro.first()) {
            generalSettings.themeStyle.value = style
        } else {
            launchUpgradeFlow.tryEmit { upgradeRepo.launchBillingFlow(it) }
        }
    }

    fun setThemeColor(color: ThemeColor) = launch {
        if (isPro.first()) {
            generalSettings.themeColor.value = color
        } else {
            launchUpgradeFlow.tryEmit { upgradeRepo.launchBillingFlow(it) }
        }
    }

    fun goToDebugSettings() {
        navTo(Nav.Settings.Debug)
    }

    companion object {
        private val TAG = logTag("Settings", "General", "VM")
    }
}
