package eu.darken.capod.reaction.core.autoconnect

import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.bluetooth.NudgeAvailability
import eu.darken.capod.common.bluetooth.NudgeCapabilityStore
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.setupCommonEventHandlers
import eu.darken.capod.main.core.GeneralSettings
import eu.darken.capod.monitor.core.DeviceMonitor
import eu.darken.capod.monitor.core.primaryDevice
import eu.darken.capod.pods.core.apple.ble.devices.DualApplePods
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoConnect @Inject constructor(
    private val bluetoothManager: BluetoothManager2,
    private val deviceMonitor: DeviceMonitor,
    private val nudgeCapabilityStore: NudgeCapabilityStore,
) {

    fun monitor(): Flow<Unit> = deviceMonitor.primaryDevice()
        .map { it?.reactions?.autoConnect == true }
        .distinctUntilChanged()
        .flatMapLatest { isAutoConnectEnabled ->
            if (isAutoConnectEnabled) {
                combine(
                    bluetoothManager.connectedDevices,
                    deviceMonitor.primaryDevice().filterNotNull().distinctUntilChangedBy {
                        Triple(it.rawDataHex, it.reactions.autoConnectCondition, it.reactions.onePodMode)
                    },
                ) { connectedDevices, mainDevice ->
                    connectedDevices to mainDevice
                }
            } else {
                emptyFlow()
            }
        }
        .map { (connectedDevices, mainDevice) ->
            log(TAG, VERBOSE) { "mainPodDevice is $mainDevice" }

            val mainDeviceAddr = mainDevice.address
            if (mainDeviceAddr.isNullOrEmpty()) {
                log(TAG, WARN) { "mainDeviceAddress is null" }
                return@map
            }

            val bondedDevice = bluetoothManager.bondedDevices().first().firstOrNull { it.address == mainDeviceAddr }

            if (bondedDevice == null) {
                log(TAG, WARN) { "No bonded device matches $mainDeviceAddr" }
                return@map
            } else {
                log(TAG, VERBOSE) { "Found target device: $bondedDevice" }
            }

            val isAlreadyConnected = connectedDevices.any {
                it.address == bondedDevice.address
            }

            if (isAlreadyConnected) {
                log(TAG) { "We are already connected to the target device: $bondedDevice" }
                return@map
            }

            val reactions = mainDevice.reactions
            val condition = reactions.autoConnectCondition
            log(TAG) { "Checking condition $condition" }

            val lidState = mainDevice.caseLidState
            val isBeingWorn = mainDevice.isBeingWorn ?: false
            val isEitherPodInEar = mainDevice.isEitherPodInEar ?: false
            val onePodMode = reactions.onePodMode

            val decision = evaluateAutoConnect(
                mainDeviceAddr = mainDeviceAddr,
                hasBondedDevice = true,
                isAlreadyConnected = false,
                condition = condition,
                lidState = lidState,
                isBeingWorn = isBeingWorn,
                isEitherPodInEar = isEitherPodInEar,
                onePodMode = onePodMode,
                supportsEarDetection = mainDevice.hasEarDetection,
            )

            if (!decision.shouldConnect) {
                log(TAG) { "Auto connect condition ($condition) is not fullfilled: ${decision.reason}" }
                return@map
            }

            if (nudgeCapabilityStore.availability.value == NudgeAvailability.BROKEN) {
                log(TAG, WARN) { "nudgeConnection is known broken on this device, skipping" }
                return@map
            }

            val result = bluetoothManager.nudgeConnection(bondedDevice)
            log(TAG) { "nudgeConnection($bondedDevice) returned $result" }
            nudgeCapabilityStore.record(result)
        }
        .setupCommonEventHandlers(TAG) { "monitor" }

    internal fun evaluateAutoConnect(
        mainDeviceAddr: String?,
        hasBondedDevice: Boolean,
        isAlreadyConnected: Boolean,
        condition: AutoConnectCondition,
        lidState: DualApplePods.LidState?,
        isBeingWorn: Boolean,
        isEitherPodInEar: Boolean,
        onePodMode: Boolean,
        supportsEarDetection: Boolean,
    ): AutoConnectDecision {
        if (mainDeviceAddr.isNullOrEmpty()) {
            return AutoConnectDecision(false, "No main device address")
        }
        if (!hasBondedDevice) {
            return AutoConnectDecision(false, "No bonded device")
        }
        if (isAlreadyConnected) {
            return AutoConnectDecision(false, "Already connected")
        }
        return when (condition) {
            AutoConnectCondition.WHEN_SEEN -> AutoConnectDecision(true, "WHEN_SEEN: device visible")
            AutoConnectCondition.CASE_OPEN -> {
                if (lidState == null) {
                    AutoConnectDecision(true, "CASE_OPEN: unsupported device, permissive fallback")
                } else when (lidState) {
                    DualApplePods.LidState.OPEN -> AutoConnectDecision(true, "CASE_OPEN: lid is open")
                    else -> AutoConnectDecision(false, "CASE_OPEN: lid is $lidState")
                }
            }
            AutoConnectCondition.IN_EAR -> {
                if (!supportsEarDetection) {
                    return AutoConnectDecision(true, "IN_EAR: unsupported device, permissive fallback")
                }
                val inEar = if (onePodMode) isEitherPodInEar else isBeingWorn
                AutoConnectDecision(inEar, if (inEar) "IN_EAR: pod in ear" else "IN_EAR: not in ear")
            }
        }
    }

    data class AutoConnectDecision(
        val shouldConnect: Boolean,
        val reason: String,
    )

    companion object {
        private val TAG = logTag("Reaction", "AutoConnect")
    }
}
