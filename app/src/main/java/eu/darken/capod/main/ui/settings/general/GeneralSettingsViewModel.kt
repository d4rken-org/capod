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
import eu.darken.capod.common.upgrade.isProForUi
import eu.darken.capod.main.core.GeneralSettings
import eu.darken.capod.main.core.themeState
import kotlinx.coroutines.flow.combine
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
        /**
         * True only when billing settled without an error and reports no entitlement — the
         * presentation mirror of what [isProForUi] would deny. While billing is still connecting
         * (GPlay cold-start seed) a paying user keeps the real controls instead of being shown the
         * upgrade branch; the setters re-check via [isProForUi] before writing.
         */
        val isUpgradeLocked: Boolean,
        val showConnectedNotification: Boolean,
        val keepNotificationAfterDisconnect: Boolean,
        val isOffloadedFilteringDisabled: Boolean,
        val isOffloadedBatchingDisabled: Boolean,
        val useIndirectScanResultCallback: Boolean,
        val hideUnmatchedDevices: Boolean,
        val themeState: ThemeState,
    )

    // Hard-locked = settled, error-free and no entitlement. Anything else (unsettled seed, error)
    // keeps the pro presentation, so the cold-start race can't route a paying user to upgrade.
    private val isUpgradeLocked = upgradeRepo.upgradeInfo
        .map { it.error == null && it.isSettled && !it.isPro }
        .asLiveState()

    val state = combine(
        combine(
            generalSettings.useExtraMonitorNotification.flow,
            generalSettings.keepConnectedNotificationAfterDisconnect.flow,
        ) { showNotif, keepNotif ->
            @Suppress("USELESS_CAST")
            arrayOf<Any>(showNotif as Any, keepNotif as Any)
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
        isUpgradeLocked,
        generalSettings.hideUnmatchedDevices.flow,
    ) { general, compat, themeState, upgradeLocked, hideUnmatched ->
        State(
            isUpgradeLocked = upgradeLocked,
            showConnectedNotification = general[0] as Boolean,
            keepNotificationAfterDisconnect = general[1] as Boolean,
            isOffloadedFilteringDisabled = compat[0] as Boolean,
            isOffloadedBatchingDisabled = compat[1] as Boolean,
            useIndirectScanResultCallback = compat[2] as Boolean,
            hideUnmatchedDevices = hideUnmatched,
            themeState = themeState,
        )
    }.asLiveState()

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

    fun setHideUnmatchedDevices(enabled: Boolean) {
        log(TAG, INFO) { "setHideUnmatchedDevices($enabled)" }
        generalSettings.hideUnmatchedDevices.valueBlocking = enabled
    }

    fun setThemeMode(mode: ThemeMode) = launch {
        log(TAG, INFO) { "setThemeMode($mode)" }
        if (upgradeRepo.isProForUi()) {
            generalSettings.themeMode.valueBlocking = mode
        } else {
            navTo(Nav.Main.Upgrade())
        }
    }

    fun setThemeStyle(style: ThemeStyle) = launch {
        log(TAG, INFO) { "setThemeStyle($style)" }
        if (upgradeRepo.isProForUi()) {
            generalSettings.themeStyle.valueBlocking = style
        } else {
            navTo(Nav.Main.Upgrade())
        }
    }

    fun setThemeColor(color: ThemeColor) = launch {
        log(TAG, INFO) { "setThemeColor($color)" }
        if (upgradeRepo.isProForUi()) {
            generalSettings.themeColor.valueBlocking = color
        } else {
            navTo(Nav.Main.Upgrade())
        }
    }

    fun launchUpgrade() {
        log(TAG, INFO) { "launchUpgrade()" }
        navTo(Nav.Main.Upgrade())
    }

    companion object {
        private val TAG = logTag("Settings", "General", "VM")
    }
}
