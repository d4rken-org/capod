package eu.darken.capod.monitor.core

import eu.darken.capod.common.coroutine.AppScope
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.replayingShare
import eu.darken.capod.monitor.core.CachedDeviceState.CachedBatterySlot
import eu.darken.capod.pods.core.apple.aap.AapConnectionManager
import eu.darken.capod.pods.core.apple.ble.DualBlePodSnapshot
import eu.darken.capod.pods.core.apple.ble.SingleBlePodSnapshot
import eu.darken.capod.pods.core.apple.ble.devices.HasCase
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.onEach
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single merge point: combines BLE scan data ([BlePodMonitor]) with AAP connection data
 * ([AapConnectionManager]) and cached device state ([DeviceStateCache]) into unified [PodDevice] objects.
 *
 * Includes cached-only devices for profiles that have cached state but no live BLE data.
 * Persists live device state to [DeviceStateCache] as a side effect.
 * ViewModels should observe [devices] instead of accessing BlePodMonitor directly.
 */
@Singleton
class DeviceMonitor @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
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
            if (!device.isLive) continue
            val profileId = device.profileId ?: continue

            val now = Instant.now()
            val existing = deviceStateCache.cachedStates.value[profileId]

            val liveLeft = device.aap?.batteryLeft ?: (device.ble as? DualBlePodSnapshot)?.batteryLeftPodPercent
            val liveRight = device.aap?.batteryRight ?: (device.ble as? DualBlePodSnapshot)?.batteryRightPodPercent
            val liveCase = device.aap?.batteryCase ?: (device.ble as? HasCase)?.batteryCasePercent
            val liveHeadset = device.aap?.batteryHeadset ?: (device.ble as? SingleBlePodSnapshot)?.batteryHeadsetPercent

            if (liveLeft == null && liveRight == null && liveCase == null && liveHeadset == null) continue

            val newState = CachedDeviceState(
                profileId = profileId,
                model = device.model,
                address = device.address,
                left = liveLeft?.let { CachedBatterySlot(it, now) } ?: existing?.left,
                right = liveRight?.let { CachedBatterySlot(it, now) } ?: existing?.right,
                case = liveCase?.let { CachedBatterySlot(it, now) } ?: existing?.case,
                headset = liveHeadset?.let { CachedBatterySlot(it, now) } ?: existing?.headset,
                isLeftCharging = device.isLeftPodCharging,
                isRightCharging = device.isRightPodCharging,
                isCaseCharging = device.isCaseCharging,
                isHeadsetCharging = device.isHeadsetBeingCharged,
                lastSeenAt = device.seenLastAt ?: now,
            )

            if (isSameState(existing, newState)) continue

            log(TAG, VERBOSE) { "Persisting state for $profileId (L=$liveLeft R=$liveRight C=$liveCase H=$liveHeadset)" }
            deviceStateCache.save(profileId, newState)
        }
    }

    private fun isSameState(old: CachedDeviceState?, new: CachedDeviceState): Boolean {
        if (old == null) return false
        if (Duration.between(old.lastSeenAt, new.lastSeenAt).abs() > Duration.ofMinutes(1)) return false
        return old.left?.percent == new.left?.percent
            && old.right?.percent == new.right?.percent
            && old.case?.percent == new.case?.percent
            && old.headset?.percent == new.headset?.percent
            && old.isLeftCharging == new.isLeftCharging
            && old.isRightCharging == new.isRightCharging
            && old.isCaseCharging == new.isCaseCharging
            && old.isHeadsetCharging == new.isHeadsetCharging
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
