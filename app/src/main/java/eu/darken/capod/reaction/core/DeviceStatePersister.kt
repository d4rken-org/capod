package eu.darken.capod.reaction.core

import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.setupCommonEventHandlers
import eu.darken.capod.monitor.core.CachedDeviceState
import eu.darken.capod.monitor.core.CachedDeviceState.CachedBatterySlot
import eu.darken.capod.monitor.core.DeviceMonitor
import eu.darken.capod.monitor.core.DeviceStateCache
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.ble.DualBlePodSnapshot
import eu.darken.capod.pods.core.apple.ble.SingleBlePodSnapshot
import eu.darken.capod.pods.core.apple.ble.devices.HasCase
import eu.darken.capod.pods.core.apple.ble.devices.HasChargeDetection
import eu.darken.capod.pods.core.apple.ble.devices.HasChargeDetectionDual
import kotlinx.coroutines.flow.Flow
import java.time.Duration
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists combined device state (battery, charging, model) to [DeviceStateCache].
 * Reads from raw BLE/AAP sources to avoid re-persisting cached values.
 */
@Singleton
class DeviceStatePersister @Inject constructor(
    private val deviceMonitor: DeviceMonitor,
    private val deviceStateCache: DeviceStateCache,
) {
    fun monitor(): Flow<Unit> = deviceMonitor.devices
        .onEach { devices ->
            for (device in devices) {
                if (!device.isLive) continue
                val profileId = device.profileId ?: continue

                val now = Instant.now()
                val existing = deviceStateCache.cachedStates.value[profileId]

                val liveLeft = device.aap?.batteryLeft ?: (device.ble as? DualBlePodSnapshot)?.batteryLeftPodPercent
                val liveRight = device.aap?.batteryRight ?: (device.ble as? DualBlePodSnapshot)?.batteryRightPodPercent
                val liveCase = device.aap?.batteryCase ?: (device.ble as? HasCase)?.batteryCasePercent
                val liveHeadset = device.aap?.batteryHeadset ?: (device.ble as? SingleBlePodSnapshot)?.batteryHeadsetPercent

                // At least one live battery must be non-null to be worth persisting
                if (liveLeft == null && liveRight == null && liveCase == null && liveHeadset == null) continue

                val newState = CachedDeviceState(
                    profileId = profileId,
                    model = device.model,
                    address = device.address,
                    left = liveLeft?.let { CachedBatterySlot(it, now) } ?: existing?.left,
                    right = liveRight?.let { CachedBatterySlot(it, now) } ?: existing?.right,
                    case = liveCase?.let { CachedBatterySlot(it, now) } ?: existing?.case,
                    headset = liveHeadset?.let { CachedBatterySlot(it, now) } ?: existing?.headset,
                    isLeftCharging = device.aap?.isLeftCharging ?: (device.ble as? HasChargeDetectionDual)?.isLeftPodCharging ?: existing?.isLeftCharging,
                    isRightCharging = device.aap?.isRightCharging ?: (device.ble as? HasChargeDetectionDual)?.isRightPodCharging ?: existing?.isRightCharging,
                    isCaseCharging = device.aap?.isCaseCharging ?: (device.ble as? HasCase)?.isCaseCharging ?: existing?.isCaseCharging,
                    isHeadsetCharging = device.aap?.isHeadsetCharging ?: (device.ble as? HasChargeDetection)?.isHeadsetBeingCharged ?: existing?.isHeadsetCharging,
                    lastSeenAt = device.seenLastAt ?: now,
                )

                if (isSameState(existing, newState)) continue

                log(TAG, VERBOSE) { "Persisting state for $profileId (L=${liveLeft} R=${liveRight} C=${liveCase} H=${liveHeadset})" }
                deviceStateCache.save(profileId, newState)
            }
        }
        .map { }
        .setupCommonEventHandlers(TAG) { "deviceStatePersister" }

    private fun isSameState(old: CachedDeviceState?, new: CachedDeviceState): Boolean {
        if (old == null) return false
        // Update lastSeenAt periodically so the staleness label stays fresh when device goes offline
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

    companion object {
        private val TAG = logTag("Reaction", "DeviceStatePersister")
    }
}
