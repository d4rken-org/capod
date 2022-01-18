package eu.darken.capod.reaction.autoconnect

import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.setupCommonEventHandlers
import eu.darken.capod.main.core.GeneralSettings
import eu.darken.capod.monitor.core.PodMonitor
import eu.darken.capod.pods.core.HasEarDetection
import eu.darken.capod.pods.core.apple.DualApplePods
import eu.darken.capod.reaction.settings.ReactionSettings
import kotlinx.coroutines.flow.*
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
        .flatMapLatest { if (it) podMonitor.mainDevice else emptyFlow() }
        .filterNotNull()
        .distinctUntilChanged()
        .map { podDevice ->
            log(TAG) { "mainPodDevice is $podDevice" }

            val mainDeviceAddr = generalSettings.mainDeviceAddress.value
            if (mainDeviceAddr.isNullOrEmpty()) {
                log(TAG) { "mainDeviceAddress is null" }
                return@map
            } else {
                log(TAG) { "mainDeviceAddress is $mainDeviceAddr" }
            }

            val bondedDevice = bluetoothManager.bondedDevices().firstOrNull { it.address == mainDeviceAddr }
            if (bondedDevice == null) {
                log(TAG) { "No bonded device matches $mainDeviceAddr" }
                return@map
            } else {
                log(TAG) { "Found target device: $bondedDevice" }
            }

            val isAlreadyConnected = bluetoothManager.connectedDevices().first().any {
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
                AutoConnectCondition.CASE_OPEN -> when (podDevice) {
                    is DualApplePods -> podDevice.caseLidState == DualApplePods.LidState.OPEN
                    else -> true
                }
                AutoConnectCondition.IN_EAR -> when (podDevice) {
                    is HasEarDetection -> podDevice.isBeingWorn
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