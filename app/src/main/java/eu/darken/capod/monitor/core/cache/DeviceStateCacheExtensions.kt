package eu.darken.capod.monitor.core.cache

import eu.darken.capod.common.SystemTimeSource
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.monitor.core.cache.CachedDeviceState.CachedBatterySlot
import eu.darken.capod.pods.core.apple.ble.BATTERY_UNKNOWN
import eu.darken.capod.pods.core.apple.ble.DualBlePodSnapshot
import eu.darken.capod.pods.core.apple.ble.SingleBlePodSnapshot
import eu.darken.capod.pods.core.apple.ble.devices.HasCase
import eu.darken.capod.pods.core.apple.ble.isKnownBattery
import java.time.Duration
import java.time.Instant

/**
 * Creates a [CachedDeviceState] from this live device's raw BLE/AAP battery data.
 * Returns null if:
 * - The device is not live (cached-only)
 * - The device has no profile
 * - All live battery values AND live DeviceInfo are null (nothing fresh to persist)
 * - The state hasn't changed from [existing] (dedup)
 */
fun PodDevice.toCachedState(
    existing: CachedDeviceState?,
    now: Instant = SystemTimeSource.now(),
): CachedDeviceState? {
    if (!isLive) return null
    val pid = profileId ?: return null

    // RAW LIVE EXTRACTION — must NOT use device.batteryLeft etc. (which fall back to cache).
    // Reading the unified getter would re-stamp stale cached readings as fresh live data.
    val liveLeft = aap?.batteryLeft ?: (ble as? DualBlePodSnapshot)?.batteryLeftPodPercent ?: BATTERY_UNKNOWN
    val liveRight = aap?.batteryRight ?: (ble as? DualBlePodSnapshot)?.batteryRightPodPercent ?: BATTERY_UNKNOWN
    val liveCase = aap?.batteryCase ?: (ble as? HasCase)?.batteryCasePercent ?: BATTERY_UNKNOWN
    val liveHeadset = aap?.batteryHeadset ?: (ble as? SingleBlePodSnapshot)?.batteryHeadsetPercent ?: BATTERY_UNKNOWN
    val liveDeviceInfo = aap?.deviceInfo

    if (!isKnownBattery(liveLeft) && !isKnownBattery(liveRight) &&
        !isKnownBattery(liveCase) && !isKnownBattery(liveHeadset) && liveDeviceInfo == null
    ) return null

    val newState = CachedDeviceState(
        profileId = pid,
        model = model,
        address = address,
        left = mergeBatterySlot(liveLeft, existing?.left, now),
        right = mergeBatterySlot(liveRight, existing?.right, now),
        case = mergeBatterySlot(liveCase, existing?.case, now),
        headset = mergeBatterySlot(liveHeadset, existing?.headset, now),
        isLeftCharging = isLeftPodCharging,
        isRightCharging = isRightPodCharging,
        isCaseCharging = isCaseCharging,
        isHeadsetCharging = isHeadsetBeingCharged,
        deviceName = liveDeviceInfo?.name ?: existing?.deviceName,
        serialNumber = liveDeviceInfo?.serialNumber ?: existing?.serialNumber,
        firmwareVersion = liveDeviceInfo?.firmwareVersion ?: existing?.firmwareVersion,
        leftEarbudSerial = liveDeviceInfo?.leftEarbudSerial ?: existing?.leftEarbudSerial,
        rightEarbudSerial = liveDeviceInfo?.rightEarbudSerial ?: existing?.rightEarbudSerial,
        marketingVersion = liveDeviceInfo?.marketingVersion ?: existing?.marketingVersion,
        lastSeenAt = seenLastAt ?: now,
    )

    if (existing != null && !hasStateChanged(existing, newState)) return null

    return newState
}

private fun mergeBatterySlot(
    livePercent: Float,
    existing: CachedBatterySlot?,
    now: Instant,
): CachedBatterySlot? {
    if (!isKnownBattery(livePercent)) return existing
    val current = existing ?: return CachedBatterySlot(livePercent, now)
    val isStale = Duration.between(current.updatedAt, now).abs() > Duration.ofMinutes(1)
    return if (current.percent == livePercent && !isStale) current else CachedBatterySlot(livePercent, now)
}

private fun hasStateChanged(old: CachedDeviceState, new: CachedDeviceState): Boolean {
    if (Duration.between(old.lastSeenAt, new.lastSeenAt).abs() > Duration.ofMinutes(1)) return true
    if (hasSlotChanged(old.left, new.left)) return true
    if (hasSlotChanged(old.right, new.right)) return true
    if (hasSlotChanged(old.case, new.case)) return true
    if (hasSlotChanged(old.headset, new.headset)) return true
    return old.isLeftCharging != new.isLeftCharging
        || old.isRightCharging != new.isRightCharging
        || old.isCaseCharging != new.isCaseCharging
        || old.isHeadsetCharging != new.isHeadsetCharging
        || old.deviceName != new.deviceName
        || old.serialNumber != new.serialNumber
        || old.firmwareVersion != new.firmwareVersion
        || old.leftEarbudSerial != new.leftEarbudSerial
        || old.rightEarbudSerial != new.rightEarbudSerial
        || old.marketingVersion != new.marketingVersion
}

private fun hasSlotChanged(old: CachedBatterySlot?, new: CachedBatterySlot?): Boolean {
    if (old == null && new == null) return false
    if (old == null || new == null) return true
    if (old.percent != new.percent) return true
    return Duration.between(old.updatedAt, new.updatedAt).abs() > Duration.ofMinutes(1)
}
