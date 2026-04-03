package eu.darken.capod.monitor.core

import eu.darken.capod.common.coroutine.AppScope
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.replayingShare
import eu.darken.capod.monitor.core.aap.AapLifecycleManager
import eu.darken.capod.monitor.core.ble.BlePodMonitor
import eu.darken.capod.monitor.core.cache.DeviceStateCache
import eu.darken.capod.monitor.core.cache.toCachedState
import eu.darken.capod.pods.core.apple.aap.AapConnectionManager
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single merge point: combines BLE scan data ([eu.darken.capod.monitor.core.ble.BlePodMonitor]) with AAP connection data
 * ([AapConnectionManager]) and cached device state ([eu.darken.capod.monitor.core.cache.DeviceStateCache]) into unified [PodDevice] objects.
 *
 * Includes cached-only devices for profiles that have cached state but no live BLE data.
 * Persists live device state to [eu.darken.capod.monitor.core.cache.DeviceStateCache] as a side effect.
 * ViewModels should observe [devices] instead of accessing BlePodMonitor directly.
 */
@Singleton
class DeviceMonitor @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val blePodMonitor: BlePodMonitor,
    private val aapManager: AapConnectionManager,
    private val deviceStateCache: DeviceStateCache,
    private val profilesRepo: DeviceProfilesRepo,
    private val aapLifecycleManager: AapLifecycleManager,
) {
    init {
        aapLifecycleManager.start()
    }

    val devices: Flow<List<PodDevice>> = combine(
        blePodMonitor.devices,
        aapManager.allStates,
        deviceStateCache.cachedStates,
        profilesRepo.profiles,
    ) { pods, aapStates, cachedStates, profiles ->
        // Live devices — BLE + AAP + cached fallback for missing fields
        val liveDevices = pods.map { pod ->
            val bondedAddress = pod.meta.profile?.address
            val profile = pod.meta.profile
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
        .onEach { devices -> persistLiveDevices(devices) }
        .replayingShare(appScope)

    private suspend fun persistLiveDevices(devices: List<PodDevice>) {
        for (device in devices) {
            val profileId = device.profileId ?: continue
            val existing = deviceStateCache.cachedStates.value[profileId]
            val newState = device.toCachedState(existing) ?: continue

            log(TAG, VERBOSE) { "Persisting state for $profileId" }
            deviceStateCache.save(profileId, newState)
        }
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
