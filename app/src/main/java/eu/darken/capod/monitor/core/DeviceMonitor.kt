package eu.darken.capod.monitor.core

import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.pods.core.apple.aap.AapConnectionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single merge point: combines BLE scan data ([BlePodMonitor]) with AAP connection data
 * ([AapConnectionManager]) into unified [PodDevice] objects.
 *
 * ViewModels should observe [devices] instead of accessing BlePodMonitor directly.
 */
@Singleton
class DeviceMonitor @Inject constructor(
    private val blePodMonitor: BlePodMonitor,
    private val aapManager: AapConnectionManager,
) {
    val devices: Flow<List<PodDevice>> = blePodMonitor.devices
        .combine(aapManager.allStates) { pods, aapStates ->
            pods.map { pod ->
                // AAP connections are keyed by bonded BR/EDR address (from profile),
                // BLE scans use rotating RPAs. Bridge via the profile's bonded address.
                val bondedAddress = pod.meta?.profile?.address
                PodDevice(
                    ble = pod,
                    aap = bondedAddress?.let { aapStates[it] },
                )
            }
        }

    suspend fun getDeviceForProfile(profileId: String): PodDevice? {
        log(TAG) { "getDeviceForProfile(profileId=$profileId)" }
        val bleDevice = blePodMonitor.getDeviceForProfile(profileId) ?: return null
        val bondedAddress = bleDevice.meta?.profile?.address
        val aapState = bondedAddress?.let { aapManager.allStates.firstOrNull()?.get(it) }
        return PodDevice(ble = bleDevice, aap = aapState)
    }

    companion object {
        private val TAG = logTag("DeviceMonitor")
    }
}
