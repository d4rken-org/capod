package eu.darken.capod.monitor.core

/**
 * Connection tier rank used to sort devices by user-perceived "primary":
 * lower rank = higher priority. System-connected devices come first, then
 * any live device (BLE or AAP), then profiled-but-offline.
 *
 * Distinct from [DeviceMonitor.primaryDevice] which is intentionally
 * non-tier-ranked for reaction flows that want "any profiled device".
 */
fun PodDevice.tierRank(): Int = when {
    isSystemConnected -> 0
    isLive -> 1
    else -> 2
}

/**
 * Picks the user-perceived primary profiled device: lowest [tierRank], with
 * the user's profile-list order as the tiebreaker.
 */
fun List<PodDevice>.primaryByTier(profileOrder: Map<String, Int>): PodDevice? =
    filter { it.profileId != null }
        .minWithOrNull(
            compareBy<PodDevice> { it.tierRank() }
                .thenBy { profileOrder[it.profileId] ?: Int.MAX_VALUE }
        )
