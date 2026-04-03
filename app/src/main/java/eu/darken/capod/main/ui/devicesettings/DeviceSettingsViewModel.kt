package eu.darken.capod.main.ui.devicesettings

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.capod.common.bluetooth.BluetoothAddress
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.uix.ViewModel4
import eu.darken.capod.monitor.core.DeviceMonitor
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.aap.AapConnectionManager
import eu.darken.capod.pods.core.apple.aap.protocol.AapCommand
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
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

    val state = targetAddress.flatMapLatest { address ->
        if (address == null) return@flatMapLatest flowOf(State(device = null))
        combine(updateTicker, deviceMonitor.devices) { _, devices ->
            State(
                device = devices.firstOrNull { it.address == address },
                now = Instant.now(),
            )
        }
    }.asLiveState()

    data class State(
        val device: PodDevice?,
        val now: Instant = Instant.now(),
    )

    private fun send(command: AapCommand) {
        val address = targetAddress.value ?: return
        launch {
            try {
                aapManager.sendCommand(address, command)
                log(TAG) { "Sent $command to $address" }
            } catch (e: Exception) {
                log(TAG) { "Failed to send $command: ${e.message}" }
            }
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

    companion object {
        private val TAG = logTag("DeviceSettings", "VM")
    }
}
