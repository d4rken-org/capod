package eu.darken.capod.reactions.core

import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.monitor.core.PodMonitor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoConnect @Inject constructor(
    private val bluetoothManager: BluetoothManager2,
    private val podMonitor: PodMonitor,
) {

    fun monitor(): Flow<Unit> = podMonitor.mainDevice
        .map {
            val podDevice = bluetoothManager2.bondedDevices().firstOrNull { it.name?.contains("AirPod") ?: false }
            bluetoothManager2.nudgeConnection(podDevice!!)
        }

    companion object {
        private val TAG = logTag("Reactions", "AutoConnect")
    }
}