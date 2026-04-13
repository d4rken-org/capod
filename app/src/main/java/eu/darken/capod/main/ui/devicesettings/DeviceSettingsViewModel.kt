package eu.darken.capod.main.ui.devicesettings

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.datastore.value
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.SingleEventFlow
import eu.darken.capod.common.uix.ViewModel4
import eu.darken.capod.main.core.GeneralSettings
import eu.darken.capod.main.core.MonitorMode
import eu.darken.capod.monitor.core.DeviceMonitor
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.common.navigation.Nav
import eu.darken.capod.common.upgrade.UpgradeRepo
import eu.darken.capod.common.upgrade.isPro
import eu.darken.capod.pods.core.apple.aap.AapConnectionManager
import eu.darken.capod.pods.core.apple.aap.protocol.AapCommand
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import eu.darken.capod.profiles.core.AppleDeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import eu.darken.capod.profiles.core.ReactionConfig
import eu.darken.capod.profiles.core.ProfileId
import eu.darken.capod.reaction.core.autoconnect.AutoConnectCondition
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class DeviceSettingsViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val deviceMonitor: DeviceMonitor,
    private val aapManager: AapConnectionManager,
    private val upgradeRepo: UpgradeRepo,
    private val bluetoothManager: BluetoothManager2,
    private val profilesRepo: DeviceProfilesRepo,
    private val generalSettings: GeneralSettings,
) : ViewModel4(dispatcherProvider) {

    private val targetProfileId = MutableStateFlow<ProfileId?>(null)
    private var initialized = false

    fun initialize(profileId: ProfileId) {
        if (initialized && targetProfileId.value == profileId) return
        initialized = true
        targetProfileId.value = profileId
    }

    private val updateTicker = channelFlow<Unit> {
        while (isActive) {
            trySend(Unit)
            delay(3000)
        }
    }

    private val isForceConnecting = MutableStateFlow(false)

    sealed interface Event {
        data object OpenBluetoothSettings : Event
        data class SendFailed(val command: AapCommand, val message: String?) : Event
        data object SystemRenameUnavailable : Event
    }

    val events = SingleEventFlow<Event>()

    val state = targetProfileId.flatMapLatest { profileId ->
        if (profileId == null) return@flatMapLatest flowOf(State(device = null))
        combine(
            updateTicker,
            deviceForProfile(profileId),
            upgradeRepo.upgradeInfo,
            isForceConnecting,
            bluetoothManager.connectedDevices,
        ) { _, device, upgrade, forcing, connectedDevices ->
            val connectedAddresses = connectedDevices.map { it.address }.toSet()
            State(
                device = device,
                now = Instant.now(),
                isPro = upgrade.isPro,
                isNudgeAvailable = bluetoothManager.isNudgeAvailable,
                isForceConnecting = forcing,
                isClassicallyConnected = device?.address?.let { it in connectedAddresses } == true,
            )
        }
    }.asLiveState()

    /**
     * Returns the [PodDevice] for [profileId] reactively. Falls back to a synthesized bare
     * device when the profile exists but isn't currently visible (no BLE / cache / AAP),
     * so the Reactions section can still be edited for pristine profiles.
     */
    private fun deviceForProfile(profileId: ProfileId): kotlinx.coroutines.flow.Flow<PodDevice?> =
        deviceMonitor.devices.flatMapLatest { devices ->
            val live = devices.firstOrNull { it.profileId == profileId }
            flow<PodDevice?> { emit(live ?: deviceMonitor.getDeviceForProfile(profileId)) }
        }

    data class State(
        val device: PodDevice?,
        val now: Instant = Instant.now(),
        val isPro: Boolean = false,
        val isNudgeAvailable: Boolean = true,
        val isForceConnecting: Boolean = false,
        val isClassicallyConnected: Boolean = false,
    ) {
        val reactions: ReactionConfig get() = device?.reactions ?: ReactionConfig()
    }

    private suspend fun currentAddress(): String? {
        val profileId = targetProfileId.value ?: return null
        return deviceMonitor.getDeviceForProfile(profileId)?.address
    }

    fun forceConnect() = launch {
        if (!isForceConnecting.compareAndSet(expect = false, update = true)) {
            log(TAG) { "forceConnect already in progress" }
            return@launch
        }
        try {
            val address = currentAddress() ?: run {
                events.tryEmit(Event.OpenBluetoothSettings)
                return@launch
            }
            val bonded = try {
                bluetoothManager.bondedDevices().first().firstOrNull { it.address == address }
            } catch (e: Exception) {
                log(TAG, WARN) { "bondedDevices() failed: ${e.message}" }
                null
            }
            if (bonded == null) {
                log(TAG, WARN) { "No bonded device for $address — opening Bluetooth settings" }
                events.tryEmit(Event.OpenBluetoothSettings)
                return@launch
            }
            if (!bluetoothManager.isNudgeAvailable) {
                events.tryEmit(Event.OpenBluetoothSettings)
                return@launch
            }
            val accepted = try {
                bluetoothManager.nudgeConnection(bonded)
            } catch (e: Exception) {
                log(TAG, WARN) { "nudgeConnection threw: ${e.message}" }
                false
            }
            log(TAG) { "nudgeConnection($bonded) accepted=$accepted" }
            if (!accepted) {
                events.tryEmit(Event.OpenBluetoothSettings)
            }
        } finally {
            isForceConnecting.value = false
        }
    }

    private suspend fun sendInternal(command: AapCommand) {
        val address = currentAddress() ?: return
        try {
            aapManager.sendCommand(address, command)
            log(TAG) { "Sent $command to $address" }
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to send $command: ${e.message}" }
            events.emit(Event.SendFailed(command, e.message))
        }
    }

    private fun send(command: AapCommand) {
        launch { sendInternal(command) }
    }

    private fun sendProGated(command: AapCommand) = launch {
        if (upgradeRepo.isPro()) {
            sendInternal(command)
        } else {
            navTo(Nav.Main.Upgrade)
        }
    }

    fun setAncMode(mode: AapSetting.AncMode.Value) = send(AapCommand.SetAncMode(mode))

    fun setConversationalAwareness(enabled: Boolean) = send(AapCommand.SetConversationalAwareness(enabled))

    fun setNcWithOneAirPod(enabled: Boolean) = send(AapCommand.SetNcWithOneAirPod(enabled))

    fun setPersonalizedVolume(enabled: Boolean) = send(AapCommand.SetPersonalizedVolume(enabled))

    fun setToneVolume(level: Int) = send(AapCommand.SetToneVolume(level))

    fun setAdaptiveAudioNoise(level: Int) = send(AapCommand.SetAdaptiveAudioNoise(level))

    fun setPressSpeed(value: AapSetting.PressSpeed.Value) = send(AapCommand.SetPressSpeed(value))

    fun setPressHoldDuration(value: AapSetting.PressHoldDuration.Value) = send(AapCommand.SetPressHoldDuration(value))

    fun setVolumeSwipe(enabled: Boolean) = send(AapCommand.SetVolumeSwipe(enabled))

    fun setVolumeSwipeLength(value: AapSetting.VolumeSwipeLength.Value) = send(AapCommand.SetVolumeSwipeLength(value))

    fun setEndCallMuteMic(
        muteMic: AapSetting.EndCallMuteMic.MuteMicMode,
        endCall: AapSetting.EndCallMuteMic.EndCallMode,
    ) = send(AapCommand.SetEndCallMuteMic(muteMic, endCall))

    fun setMicrophoneMode(mode: AapSetting.MicrophoneMode.Mode) = send(AapCommand.SetMicrophoneMode(mode))

    fun setEarDetectionEnabled(enabled: Boolean) = send(AapCommand.SetEarDetectionEnabled(enabled))

    fun setListeningModeCycle(modeMask: Int) = sendProGated(AapCommand.SetListeningModeCycle(modeMask))

    fun setAllowOffOption(enabled: Boolean) = sendProGated(AapCommand.SetAllowOffOption(enabled))

    fun setSleepDetection(enabled: Boolean) = sendProGated(AapCommand.SetSleepDetection(enabled))

    fun setDeviceName(name: String) = launch {
        val address = currentAddress() ?: return@launch
        try {
            aapManager.sendCommand(address, AapCommand.SetDeviceName(name))
            log(TAG) { "Sent SetDeviceName to $address" }
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to send SetDeviceName: ${e.message}" }
            events.emit(Event.SendFailed(AapCommand.SetDeviceName(name), e.message))
        }

        val bonded = try {
            bluetoothManager.bondedDevices().first().firstOrNull { it.address == address }
        } catch (e: Exception) {
            log(TAG, WARN) { "bondedDevices() failed while renaming: ${e.message}" }
            null
        }
        val aliasOk = bonded?.let { bluetoothManager.setDeviceAlias(it, name) } ?: false
        if (!aliasOk) {
            log(TAG, WARN) { "System bond alias rename failed for $address — user must rename in system settings or re-pair" }
            events.emit(Event.SystemRenameUnavailable)
        }
    }

    // ── Reaction toggles (per-profile) ───────────────────────────────────────

    private suspend fun updateProfileNow(transform: (AppleDeviceProfile) -> AppleDeviceProfile) {
        val profileId = targetProfileId.value ?: return
        try {
            profilesRepo.updateAppleProfile(profileId, transform)
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to update profile $profileId: ${e.message}" }
            errorEvents.emit(e)
        }
    }

    private fun proGatedReaction(
        enabled: Boolean,
        transform: (AppleDeviceProfile) -> AppleDeviceProfile,
    ) = launch {
        // Disabling never requires pro; enabling does.
        if (enabled && !upgradeRepo.isPro()) {
            navTo(Nav.Main.Upgrade)
            return@launch
        }
        updateProfileNow(transform)
    }

    fun setOnePodMode(enabled: Boolean) = launch { updateProfileNow { it.copy(onePodMode = enabled) } }

    fun setAutoPlay(enabled: Boolean) = proGatedReaction(enabled) { it.copy(autoPlay = enabled) }

    fun setAutoPause(enabled: Boolean) = proGatedReaction(enabled) { it.copy(autoPause = enabled) }

    fun setAutoConnect(enabled: Boolean) = launch {
        updateProfileNow { it.copy(autoConnect = enabled) }
        if (enabled) {
            // Auto-connect requires ALWAYS mode to react to BLE/case/ear events when
            // nothing is connected. We intentionally do NOT revert on disable — the user
            // may have set ALWAYS manually, or another profile may still need it.
            if (generalSettings.monitorMode.value() != MonitorMode.ALWAYS) {
                log(TAG) { "Forcing monitorMode to ALWAYS because autoConnect was enabled" }
                generalSettings.monitorMode.value(MonitorMode.ALWAYS)
            }
        }
    }

    fun setAutoConnectCondition(condition: AutoConnectCondition) = launch {
        updateProfileNow { it.copy(autoConnectCondition = condition) }
    }

    fun setShowPopUpOnCaseOpen(enabled: Boolean) =
        proGatedReaction(enabled) { it.copy(showPopUpOnCaseOpen = enabled) }

    fun setShowPopUpOnConnection(enabled: Boolean) =
        proGatedReaction(enabled) { it.copy(showPopUpOnConnection = enabled) }

    fun navToStemConfig() = launch {
        if (upgradeRepo.isPro()) {
            navTo(Nav.Main.StemActionConfig)
        } else {
            navTo(Nav.Main.Upgrade)
        }
    }

    fun launchUpgrade() {
        navTo(Nav.Main.Upgrade)
    }

    companion object {
        private val TAG = logTag("DeviceSettings", "VM")
    }
}
