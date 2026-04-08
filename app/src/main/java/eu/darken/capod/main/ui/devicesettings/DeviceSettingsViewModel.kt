package eu.darken.capod.main.ui.devicesettings

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.capod.common.bluetooth.BluetoothAddress
import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.SingleEventFlow
import eu.darken.capod.common.uix.ViewModel4
import eu.darken.capod.monitor.core.DeviceMonitor
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.common.navigation.Nav
import eu.darken.capod.common.upgrade.UpgradeRepo
import eu.darken.capod.common.upgrade.isPro
import eu.darken.capod.pods.core.apple.aap.AapConnectionManager
import eu.darken.capod.pods.core.apple.aap.protocol.AapCommand
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
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
) : ViewModel4(dispatcherProvider) {

    private val targetAddress = MutableStateFlow<BluetoothAddress?>(null)
    private var initialized = false

    fun initialize(address: BluetoothAddress) {
        if (initialized && targetAddress.value == address) return
        initialized = true
        targetAddress.value = address
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

    val state = targetAddress.flatMapLatest { address ->
        if (address == null) return@flatMapLatest flowOf(State(device = null))
        combine(
            updateTicker,
            deviceMonitor.devices,
            upgradeRepo.upgradeInfo,
            isForceConnecting,
        ) { _, devices, upgrade, forcing ->
            State(
                device = devices.firstOrNull { it.address == address },
                now = Instant.now(),
                isPro = upgrade.isPro,
                isNudgeAvailable = bluetoothManager.isNudgeAvailable,
                isForceConnecting = forcing,
            )
        }
    }.asLiveState()

    data class State(
        val device: PodDevice?,
        val now: Instant = Instant.now(),
        val isPro: Boolean = false,
        val isNudgeAvailable: Boolean = true,
        val isForceConnecting: Boolean = false,
    )

    fun forceConnect() = launch {
        if (!isForceConnecting.compareAndSet(expect = false, update = true)) {
            log(TAG) { "forceConnect already in progress" }
            return@launch
        }
        try {
            val address = targetAddress.value ?: run {
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
            // On accepted=true, AapAutoConnect.initialConnect() will pick up the new
            // connectedDevices entry and trigger the AAP handshake. The card disappears
            // when device.isAapConnected becomes true.
        } finally {
            isForceConnecting.value = false
        }
    }

    private suspend fun sendInternal(command: AapCommand) {
        val address = targetAddress.value ?: return
        try {
            aapManager.sendCommand(address, command)
            log(TAG) { "Sent $command to $address" }
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to send $command: ${e.message}" }
            // SingleEventFlow is backed by a BUFFERED Channel — use the suspending emit to avoid
            // dropping the event under momentary backpressure.
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
        val address = targetAddress.value ?: return@launch
        sendInternal(AapCommand.SetDeviceName(name))

        // Also try to update the Android-local bond alias so the new name shows in Android's
        // Bluetooth settings too. The AAP rename only changes what the AirPods themselves report;
        // Android's system display reads from the bond database and needs a separate update.
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

    fun navToStemConfig() = launch {
        if (upgradeRepo.isPro()) {
            navTo(Nav.Main.StemActionConfig)
        } else {
            navTo(Nav.Main.Upgrade)
        }
    }

    companion object {
        private val TAG = logTag("DeviceSettings", "VM")
    }
}
