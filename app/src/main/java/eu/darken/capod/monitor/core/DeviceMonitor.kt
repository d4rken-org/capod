package eu.darken.capod.monitor.core

import eu.darken.capod.pods.core.apple.protocol.aap.AapConnectionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single merge point: combines BLE scan data ([PodMonitor]) with AAP connection data
 * ([AapConnectionManager]) into unified [MonitoredDevice] objects.
 *
 * ViewModels should observe [devices] instead of accessing PodMonitor directly.
 *
 * TODO: Rename to PodMonitor once old PodMonitor is renamed to BlePodMonitor.
 */
@Singleton
class DeviceMonitor @Inject constructor(
    private val podMonitor: PodMonitor,
    private val aapManager: AapConnectionManager,
) {
    val devices: Flow<List<MonitoredDevice>> = podMonitor.devices
        .combine(aapManager.allStates) { pods, aapStates ->
            pods.map { pod ->
                MonitoredDevice(
                    ble = pod,
                    aap = aapStates[pod.address],
                )
            }
        }
}
