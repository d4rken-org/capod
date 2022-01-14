package eu.darken.capod.reaction.core.autoconnect

import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.setupCommonEventHandlers
import eu.darken.capod.monitor.core.PodMonitor
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoConnect @Inject constructor(
    private val bluetoothManager: BluetoothManager2,
    private val podMonitor: PodMonitor,
) {

    fun monitor(): Flow<Unit> = podMonitor.mainDevice
        .distinctUntilChangedBy { it?.identifier }
        .onEach { mainPodDevice ->
            if (mainPodDevice == null) {
                log(TAG) { "MainPodDevice is null" }
                return@onEach
            }

            val podName = "AirPod"

            val bondedDevice = bluetoothManager.bondedDevices()
                .firstOrNull { it.name?.contains("AirPod") ?: false }
            if (bondedDevice == null) {
                log(TAG) { "No bonded device matches $podName" }
                return@onEach
            } else {
                log(TAG) { "Found target device: $bondedDevice" }
            }

            val connectedDevice = bluetoothManager.connectedDevices()
                .firstOrNull()
                ?.firstOrNull { it.name?.contains("podName") ?: false }
            if (connectedDevice?.address == bondedDevice.address) {
                log(TAG) { "We are already connected to the target device: $connectedDevice" }
                return@onEach
            }

            bluetoothManager.nudgeConnection(bondedDevice)
        }
        .map { Unit }
        .setupCommonEventHandlers(TAG) { "monitor" }

    companion object {
        private val TAG = logTag("Reaction", "AutoConnect")
    }
}