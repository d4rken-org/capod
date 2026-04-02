package eu.darken.capod.monitor.core

import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.pods.core.apple.aap.AapConnectionManager
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single merge point: combines BLE scan data ([BlePodMonitor]) with AAP connection data
 * ([AapConnectionManager]) and cached device state ([DeviceStateCache]) into unified [PodDevice] objects.
 *
 * Includes cached-only devices for profiles that have cached state but no live BLE data.
 * ViewModels should observe [devices] instead of accessing BlePodMonitor directly.
 */
@Singleton
class DeviceMonitor @Inject constructor(
    private val blePodMonitor: BlePodMonitor,
    private val aapManager: AapConnectionManager,
    private val deviceStateCache: DeviceStateCache,
    private val profilesRepo: DeviceProfilesRepo,
) {
    val devices: Flow<List<PodDevice>> = combine(
        blePodMonitor.devices,
        aapManager.allStates,
        deviceStateCache.cachedStates,
        profilesRepo.profiles,
    ) { pods, aapStates, cachedStates, profiles ->
        // Live devices — BLE + AAP + cached fallback for missing fields
        val liveDevices = pods.map { pod ->
            val bondedAddress = pod.meta?.profile?.address
            val profile = pod.meta?.profile
            PodDevice(
                profileId = profile?.id,
                label = profile?.label,
                ble = pod,
                aap = bondedAddress?.let { aapStates[it] },
                cached = profile?.id?.let { cachedStates[it] },
            )
        }

        // Cached-only devices — profiles with cache but no live BLE
        val liveProfileIds = liveDevices.mapNotNull { it.profileId }.toSet()
        val cachedOnlyDevices = profiles
            .filter { it.id !in liveProfileIds }
            .mapNotNull { profile ->
                cachedStates[profile.id]?.let {
                    PodDevice(profileId = profile.id, label = profile.label, ble = null, aap = null, cached = it)
                }
            }

        liveDevices + cachedOnlyDevices
    }

    suspend fun getDeviceForProfile(profileId: String): PodDevice? {
        log(TAG) { "getDeviceForProfile(profileId=$profileId)" }

        val liveDevice = devices.firstOrNull()?.firstOrNull { it.profileId == profileId }
        if (liveDevice != null) {
            log(TAG) { "Found live device for profile $profileId" }
            return liveDevice
        }

        val cached = deviceStateCache.load(profileId)
        if (cached != null) {
            log(TAG) { "Found cached state for profile $profileId" }
            val profileLabel = profilesRepo.profiles.firstOrNull()?.firstOrNull { it.id == profileId }?.label
            return PodDevice(profileId = profileId, label = profileLabel, ble = null, aap = null, cached = cached)
        }

        log(TAG) { "No device found for profile $profileId" }
        return null
    }

    companion object {
        private val TAG = logTag("DeviceMonitor")
    }
}
