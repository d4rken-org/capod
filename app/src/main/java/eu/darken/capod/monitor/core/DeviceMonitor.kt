package eu.darken.capod.monitor.core

import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.pods.core.apple.protocol.aap.AapConnectionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single merge point: combines BLE scan data ([BlePodMonitor]) with AAP connection data
 * ([AapConnectionManager]) into unified [MonitoredDevice] objects.
 *
 * ViewModels should observe [devices] instead of accessing BlePodMonitor directly.
 */
@Singleton
class DeviceMonitor @Inject constructor(
    private val blePodMonitor: BlePodMonitor,
    private val aapManager: AapConnectionManager,
) {
    val devices: Flow<List<MonitoredDevice>> = blePodMonitor.devices
        .combine(aapManager.allStates) { pods, aapStates ->
            pods.map { pod ->
                MonitoredDevice(
                    ble = pod,
                    aap = aapStates[pod.address],
                )
            }
        }

    suspend fun getDeviceForProfile(profileId: String): MonitoredDevice? {
        log(TAG) { "getDeviceForProfile(profileId=$profileId)" }
        val bleDevice = blePodMonitor.getDeviceForProfile(profileId) ?: return null
        val aapState = aapManager.allStates.firstOrNull()?.get(bleDevice.address)
        return MonitoredDevice(ble = bleDevice, aap = aapState)
    }

    companion object {
        private val TAG = logTag("DeviceMonitor")
    }
}
