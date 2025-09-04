package eu.darken.capod.reaction.core.autoconnect

import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.setupCommonEventHandlers
import eu.darken.capod.main.core.GeneralSettings
import eu.darken.capod.monitor.core.PodMonitor
import eu.darken.capod.pods.core.HasEarDetection
import eu.darken.capod.pods.core.HasEarDetectionDual
import eu.darken.capod.pods.core.apple.DualApplePods
import eu.darken.capod.reaction.core.ReactionSettings
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
    private val podMonitor: PodMonitor,
    private val generalSettings: GeneralSettings,
    private val reactionSettings: ReactionSettings
) {

    fun monitor(): Flow<Unit> = reactionSettings.autoConnect.flow
        .flatMapLatest { isAutoConnectEnabled ->
            if (isAutoConnectEnabled) {
                combine(
                    bluetoothManager.connectedDevices().distinctUntilChanged(),
                    podMonitor.mainDevice.filterNotNull().distinctUntilChangedBy { it.rawDataHex },
                ) { connectedDevices, mainDevice ->
                    connectedDevices to mainDevice
                }
            } else {
                emptyFlow()
            }
        }
        .map { (connectedDevices, mainDevice) ->
            log(TAG, VERBOSE) { "mainPodDevice is $mainDevice" }

            val mainDeviceAddr = generalSettings.mainDeviceAddress.value
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

            val condition = reactionSettings.autoConnectCondition.value
            log(TAG) { "Checking condition $condition" }
            val conditionFulfilled = when (condition) {
                AutoConnectCondition.WHEN_SEEN -> true
                AutoConnectCondition.CASE_OPEN -> when (mainDevice) {
                    is DualApplePods -> mainDevice.caseLidState == DualApplePods.LidState.OPEN
                    else -> true
                }
                AutoConnectCondition.IN_EAR -> when (mainDevice) {
                    is HasEarDetection -> {
                        if (mainDevice is HasEarDetectionDual && reactionSettings.onePodMode.value) {
                            mainDevice.isEitherPodInEar
                        } else {
                            mainDevice.isBeingWorn
                        }
                    }
                    else -> true
                }
            }
            if (!conditionFulfilled) {
                log(TAG) { "Auto connect condition ($condition) is not fullfilled." }
                return@map
            }
            val result = bluetoothManager.nudgeConnection(bondedDevice)
            log(TAG) { "nudgeConnection($bondedDevice) returned $result" }
        }
        .setupCommonEventHandlers(TAG) { "monitor" }

    companion object {
        private val TAG = logTag("Reaction", "AutoConnect")
    }
}