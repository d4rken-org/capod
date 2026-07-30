package eu.darken.capod.monitor.core

/**
 * Connection tier rank used to sort devices by user-perceived "primary":
 * lower rank = higher priority. System-connected devices come first, then
 * any live device (BLE or AAP), then profiled-but-offline.
 *
 * Backs [DeviceMonitor.primaryDeviceByTier], used by display surfaces (ongoing notification,
 * case popup) that should follow the worn/connected pod.
 *
 * Distinct from [DeviceMonitor.primaryDevice], which is intentionally non-tier-ranked: address-
 * and eligibility-gated reaction flows (auto-connect targets a not-yet-connected device; sleep and
 * conversation match by the event's source address) must keep "first profiled device" semantics,
 * which tier ranking would break.
 */
fun PodDevice.tierRank(): Int = when {
    isSystemConnected -> 0
    isLive -> 1
    else -> 2
}

/**
 * Sort order for the user-perceived primary profiled device: lowest [tierRank] first, then the
 * user's profile-list order, which `profiles_priority_hint` promises is authoritative.
 *
 * Shared by [primaryByTier] and the dashboard's device list so the notification, the popup, the
 * quick-settings tile and the dashboard card can never disagree about which device is primary.
 */
fun podDeviceTierComparator(profileOrder: Map<String, Int>): Comparator<PodDevice> =
    compareBy<PodDevice> { it.tierRank() }
        .thenBy { profileOrder[it.profileId] ?: Int.MAX_VALUE }

/**
 * Picks the user-perceived primary profiled device, per [podDeviceTierComparator].
 */
fun List<PodDevice>.primaryByTier(profileOrder: Map<String, Int>): PodDevice? =
    filter { it.profileId != null }
        .minWithOrNull(podDeviceTierComparator(profileOrder))
