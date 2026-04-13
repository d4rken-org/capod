package eu.darken.capod.monitor.core.cache

import eu.darken.capod.common.SystemTimeSource
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.monitor.core.cache.CachedDeviceState.CachedBatterySlot
import eu.darken.capod.pods.core.apple.ble.DualBlePodSnapshot
import eu.darken.capod.pods.core.apple.ble.SingleBlePodSnapshot
import eu.darken.capod.pods.core.apple.ble.devices.HasCase
import java.time.Duration
import java.time.Instant

/**
 * Creates a [CachedDeviceState] from this live device's raw BLE/AAP battery data.
 * Returns null if:
 * - The device is not live (cached-only)
 * - The device has no profile
 * - All live battery values are null
 * - The state hasn't changed from [existing] (dedup)
 */
fun PodDevice.toCachedState(
    existing: CachedDeviceState?,
    now: Instant = SystemTimeSource.now(),
): CachedDeviceState? {
    if (!isLive) return null
    val pid = profileId ?: return null

    val liveLeft = aap?.batteryLeft ?: (ble as? DualBlePodSnapshot)?.batteryLeftPodPercent
    val liveRight = aap?.batteryRight ?: (ble as? DualBlePodSnapshot)?.batteryRightPodPercent
    val liveCase = aap?.batteryCase ?: (ble as? HasCase)?.batteryCasePercent
    val liveHeadset = aap?.batteryHeadset ?: (ble as? SingleBlePodSnapshot)?.batteryHeadsetPercent

    if (liveLeft == null && liveRight == null && liveCase == null && liveHeadset == null) return null

    val liveDeviceInfo = aap?.deviceInfo

    val newState = CachedDeviceState(
        profileId = pid,
        model = model,
        address = address,
        left = liveLeft?.let { CachedBatterySlot(it, now) } ?: existing?.left,
        right = liveRight?.let { CachedBatterySlot(it, now) } ?: existing?.right,
        case = liveCase?.let { CachedBatterySlot(it, now) } ?: existing?.case,
        headset = liveHeadset?.let { CachedBatterySlot(it, now) } ?: existing?.headset,
        isLeftCharging = isLeftPodCharging,
        isRightCharging = isRightPodCharging,
        isCaseCharging = isCaseCharging,
        isHeadsetCharging = isHeadsetBeingCharged,
        deviceName = liveDeviceInfo?.name ?: existing?.deviceName,
        serialNumber = liveDeviceInfo?.serialNumber ?: existing?.serialNumber,
        firmwareVersion = liveDeviceInfo?.firmwareVersion ?: existing?.firmwareVersion,
        lastSeenAt = seenLastAt ?: now,
    )

    if (existing != null && !hasStateChanged(existing, newState)) return null

    return newState
}

private fun hasStateChanged(old: CachedDeviceState, new: CachedDeviceState): Boolean {
    if (Duration.between(old.lastSeenAt, new.lastSeenAt).abs() > Duration.ofMinutes(1)) return true
    return old.left?.percent != new.left?.percent
        || old.right?.percent != new.right?.percent
        || old.case?.percent != new.case?.percent
        || old.headset?.percent != new.headset?.percent
        || old.isLeftCharging != new.isLeftCharging
        || old.isRightCharging != new.isRightCharging
        || old.isCaseCharging != new.isCaseCharging
        || old.isHeadsetCharging != new.isHeadsetCharging
        || old.deviceName != new.deviceName
        || old.serialNumber != new.serialNumber
        || old.firmwareVersion != new.firmwareVersion
}
