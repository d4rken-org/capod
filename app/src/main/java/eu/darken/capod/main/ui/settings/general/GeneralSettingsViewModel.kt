package eu.darken.capod.main.ui.settings.general

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.logging.Logging.Priority.INFO
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
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
import eu.darken.capod.common.datastore.valueBlocking

@HiltViewModel
class GeneralSettingsViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val generalSettings: GeneralSettings,
    private val upgradeRepo: UpgradeRepo,
) : ViewModel4(dispatcherProvider) {

    data class State(
        val isPro: Boolean,
        val monitorMode: MonitorMode,
        val showConnectedNotification: Boolean,
        val keepNotificationAfterDisconnect: Boolean,
        val isOffloadedFilteringDisabled: Boolean,
        val isOffloadedBatchingDisabled: Boolean,
        val useIndirectScanResultCallback: Boolean,
        val themeState: ThemeState,
    )

    private val isPro = upgradeRepo.upgradeInfo.map { it.isPro }.asLiveState()

    val state = combine(
        combine(
            generalSettings.monitorMode.flow,
            generalSettings.useExtraMonitorNotification.flow,
            generalSettings.keepConnectedNotificationAfterDisconnect.flow,
        ) { monitorMode, showNotif, keepNotif ->
            @Suppress("USELESS_CAST")
            arrayOf<Any>(monitorMode as Any, showNotif as Any, keepNotif as Any)
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
            showConnectedNotification = general[1] as Boolean,
            keepNotificationAfterDisconnect = general[2] as Boolean,
            isOffloadedFilteringDisabled = compat[0] as Boolean,
            isOffloadedBatchingDisabled = compat[1] as Boolean,
            useIndirectScanResultCallback = compat[2] as Boolean,
            themeState = themeState,
        )
    }.asLiveState()

    fun setMonitorMode(mode: MonitorMode) {
        log(TAG, INFO) { "setMonitorMode($mode)" }
        generalSettings.monitorMode.valueBlocking = mode
    }

    fun setShowConnectedNotification(enabled: Boolean) {
        log(TAG, INFO) { "setShowConnectedNotification($enabled)" }
        generalSettings.useExtraMonitorNotification.valueBlocking = enabled
    }

    fun setKeepNotificationAfterDisconnect(enabled: Boolean) {
        log(TAG, INFO) { "setKeepNotificationAfterDisconnect($enabled)" }
        generalSettings.keepConnectedNotificationAfterDisconnect.valueBlocking = enabled
    }

    fun setOffloadedFilteringDisabled(disabled: Boolean) {
        log(TAG, INFO) { "setOffloadedFilteringDisabled($disabled)" }
        generalSettings.isOffloadedFilteringDisabled.valueBlocking = disabled
    }

    fun setOffloadedBatchingDisabled(disabled: Boolean) {
        log(TAG, INFO) { "setOffloadedBatchingDisabled($disabled)" }
        generalSettings.isOffloadedBatchingDisabled.valueBlocking = disabled
    }

    fun setUseIndirectScanResultCallback(enabled: Boolean) {
        log(TAG, INFO) { "setUseIndirectScanResultCallback($enabled)" }
        generalSettings.useIndirectScanResultCallback.valueBlocking = enabled
    }

    fun setThemeMode(mode: ThemeMode) = launch {
        log(TAG, INFO) { "setThemeMode($mode)" }
        if (isPro.first()) {
            generalSettings.themeMode.valueBlocking = mode
        } else {
            navTo(Nav.Main.Upgrade)
        }
    }

    fun setThemeStyle(style: ThemeStyle) = launch {
        log(TAG, INFO) { "setThemeStyle($style)" }
        if (isPro.first()) {
            generalSettings.themeStyle.valueBlocking = style
        } else {
            navTo(Nav.Main.Upgrade)
        }
    }

    fun setThemeColor(color: ThemeColor) = launch {
        log(TAG, INFO) { "setThemeColor($color)" }
        if (isPro.first()) {
            generalSettings.themeColor.valueBlocking = color
        } else {
            navTo(Nav.Main.Upgrade)
        }
    }

    fun launchUpgrade() {
        log(TAG, INFO) { "launchUpgrade()" }
        navTo(Nav.Main.Upgrade)
    }

    fun goToDebugSettings() {
        log(TAG, INFO) { "goToDebugSettings()" }
        navTo(Nav.Settings.Debug)
    }

    companion object {
        private val TAG = logTag("Settings", "General", "VM")
    }
}
