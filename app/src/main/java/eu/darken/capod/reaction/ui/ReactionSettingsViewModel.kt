package eu.darken.capod.reaction.ui

import android.app.Activity
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.shareLatest
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

    private val isPro = upgradeRepo.upgradeInfo.map { it.isPro }.shareLatest(scope = vmScope)

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
    }.shareLatest(scope = vmScope)

    fun setOnePodMode(enabled: Boolean) {
        reactionSettings.onePodMode.value = enabled
    }

    fun setAutoPlay(enabled: Boolean, activity: Activity) = launch {
        if (!enabled) {
            reactionSettings.autoPlay.value = false
            return@launch
        }
        if (isPro.first()) {
            reactionSettings.autoPlay.value = true
        } else {
            upgradeRepo.launchBillingFlow(activity)
        }
    }

    fun setAutoPause(enabled: Boolean, activity: Activity) = launch {
        if (!enabled) {
            reactionSettings.autoPause.value = false
            return@launch
        }
        if (isPro.first()) {
            reactionSettings.autoPause.value = true
        } else {
            upgradeRepo.launchBillingFlow(activity)
        }
    }

    fun setAutoConnect(enabled: Boolean, activity: Activity) = launch {
        if (!enabled) {
            reactionSettings.autoConnect.value = false
            return@launch
        }
        if (isPro.first()) {
            reactionSettings.autoConnect.value = true
            generalSettings.monitorMode.value = MonitorMode.ALWAYS
        } else {
            upgradeRepo.launchBillingFlow(activity)
        }
    }

    fun setAutoConnectCondition(condition: AutoConnectCondition) {
        reactionSettings.autoConnectCondition.value = condition
    }

    fun setShowPopUpOnCaseOpen(enabled: Boolean, activity: Activity) = launch {
        if (!enabled) {
            reactionSettings.showPopUpOnCaseOpen.value = false
            return@launch
        }
        if (isPro.first()) {
            reactionSettings.showPopUpOnCaseOpen.value = true
        } else {
            upgradeRepo.launchBillingFlow(activity)
        }
    }

    fun setShowPopUpOnConnection(enabled: Boolean, activity: Activity) = launch {
        if (!enabled) {
            reactionSettings.showPopUpOnConnection.value = false
            return@launch
        }
        if (isPro.first()) {
            reactionSettings.showPopUpOnConnection.value = true
        } else {
            upgradeRepo.launchBillingFlow(activity)
        }
    }

    companion object {
        private val TAG = logTag("Settings", "Reaction", "VM")
    }
}
