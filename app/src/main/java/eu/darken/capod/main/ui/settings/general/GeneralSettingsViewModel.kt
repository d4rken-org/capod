package eu.darken.capod.main.ui.settings.general

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.capod.common.bluetooth.ScannerMode
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.shareLatest
import eu.darken.capod.common.navigation.Nav
import eu.darken.capod.common.uix.ViewModel4
import eu.darken.capod.main.core.GeneralSettings
import eu.darken.capod.main.core.MonitorMode
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

@HiltViewModel
class GeneralSettingsViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val generalSettings: GeneralSettings,
) : ViewModel4(dispatcherProvider) {

    data class State(
        val monitorMode: MonitorMode,
        val scannerMode: ScannerMode,
        val showConnectedNotification: Boolean,
        val keepNotificationAfterDisconnect: Boolean,
        val isOffloadedFilteringDisabled: Boolean,
        val isOffloadedBatchingDisabled: Boolean,
        val useIndirectScanResultCallback: Boolean,
    )

    val state = combine(
        generalSettings.monitorMode.flow,
        generalSettings.scannerMode.flow,
        generalSettings.useExtraMonitorNotification.flow,
        generalSettings.keepConnectedNotificationAfterDisconnect.flow,
        generalSettings.isOffloadedFilteringDisabled.flow,
        generalSettings.isOffloadedBatchingDisabled.flow,
        generalSettings.useIndirectScanResultCallback.flow,
    ) { values ->
        State(
            monitorMode = values[0] as MonitorMode,
            scannerMode = values[1] as ScannerMode,
            showConnectedNotification = values[2] as Boolean,
            keepNotificationAfterDisconnect = values[3] as Boolean,
            isOffloadedFilteringDisabled = values[4] as Boolean,
            isOffloadedBatchingDisabled = values[5] as Boolean,
            useIndirectScanResultCallback = values[6] as Boolean,
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

    fun goToDebugSettings() {
        navTo(Nav.Settings.Debug)
    }

    companion object {
        private val TAG = logTag("Settings", "General", "VM")
    }
}
