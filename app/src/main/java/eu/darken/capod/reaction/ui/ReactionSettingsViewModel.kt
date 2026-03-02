package eu.darken.capod.reaction.ui

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.navigation.Nav
import eu.darken.capod.common.uix.ViewModel4
import eu.darken.capod.common.upgrade.UpgradeRepo
import eu.darken.capod.main.core.GeneralSettings
import eu.darken.capod.main.core.MonitorMode
import eu.darken.capod.reaction.core.ReactionSettings
import eu.darken.capod.reaction.core.autoconnect.AutoConnectCondition
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import eu.darken.capod.common.datastore.valueBlocking

@HiltViewModel
class ReactionSettingsViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val reactionSettings: ReactionSettings,
    private val generalSettings: GeneralSettings,
    private val upgradeRepo: UpgradeRepo,
) : ViewModel4(dispatcherProvider) {

    data class State(
        val isPro: Boolean,
        val onePodMode: Boolean,
        val autoPlay: Boolean,
        val autoPause: Boolean,
        val autoConnect: Boolean,
        val autoConnectCondition: AutoConnectCondition,
        val showPopUpOnCaseOpen: Boolean,
        val showPopUpOnConnection: Boolean,
    )

    private val isPro = upgradeRepo.upgradeInfo.map { it.isPro }.asLiveState()

    val state = combine(
        isPro,
        reactionSettings.onePodMode.flow,
        reactionSettings.autoPlay.flow,
        reactionSettings.autoPause.flow,
        reactionSettings.autoConnect.flow,
        reactionSettings.autoConnectCondition.flow,
        reactionSettings.showPopUpOnCaseOpen.flow,
        reactionSettings.showPopUpOnConnection.flow,
    ) { values ->
        State(
            isPro = values[0] as Boolean,
            onePodMode = values[1] as Boolean,
            autoPlay = values[2] as Boolean,
            autoPause = values[3] as Boolean,
            autoConnect = values[4] as Boolean,
            autoConnectCondition = values[5] as AutoConnectCondition,
            showPopUpOnCaseOpen = values[6] as Boolean,
            showPopUpOnConnection = values[7] as Boolean,
        )
    }.asLiveState()

    fun setOnePodMode(enabled: Boolean) {
        reactionSettings.onePodMode.valueBlocking = enabled
    }

    fun setAutoPlay(enabled: Boolean) = launch {
        if (!enabled) {
            reactionSettings.autoPlay.valueBlocking = false
            return@launch
        }
        if (isPro.first()) {
            reactionSettings.autoPlay.valueBlocking = true
        } else {
            navTo(Nav.Main.Upgrade)
        }
    }

    fun setAutoPause(enabled: Boolean) = launch {
        if (!enabled) {
            reactionSettings.autoPause.valueBlocking = false
            return@launch
        }
        if (isPro.first()) {
            reactionSettings.autoPause.valueBlocking = true
        } else {
            navTo(Nav.Main.Upgrade)
        }
    }

    fun setAutoConnect(enabled: Boolean) = launch {
        if (!enabled) {
            reactionSettings.autoConnect.valueBlocking = false
            return@launch
        }
        if (isPro.first()) {
            reactionSettings.autoConnect.valueBlocking = true
            generalSettings.monitorMode.valueBlocking = MonitorMode.ALWAYS
        } else {
            navTo(Nav.Main.Upgrade)
        }
    }

    fun setAutoConnectCondition(condition: AutoConnectCondition) {
        reactionSettings.autoConnectCondition.valueBlocking = condition
    }

    fun setShowPopUpOnCaseOpen(enabled: Boolean) = launch {
        if (!enabled) {
            reactionSettings.showPopUpOnCaseOpen.valueBlocking = false
            return@launch
        }
        if (isPro.first()) {
            reactionSettings.showPopUpOnCaseOpen.valueBlocking = true
        } else {
            navTo(Nav.Main.Upgrade)
        }
    }

    fun setShowPopUpOnConnection(enabled: Boolean) = launch {
        if (!enabled) {
            reactionSettings.showPopUpOnConnection.valueBlocking = false
            return@launch
        }
        if (isPro.first()) {
            reactionSettings.showPopUpOnConnection.valueBlocking = true
        } else {
            navTo(Nav.Main.Upgrade)
        }
    }

    companion object {
        private val TAG = logTag("Settings", "Reaction", "VM")
    }
}
