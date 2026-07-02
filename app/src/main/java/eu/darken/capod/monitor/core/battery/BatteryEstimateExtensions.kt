package eu.darken.capod.monitor.core.battery

import eu.darken.capod.monitor.core.PodDevice

/**
 * Returns this estimate only if [device] should display it: the per-device toggle is on, the
 * device is live (no estimates for cached/offline cards), and it maps to a profile.
 */
fun BatteryEstimate.takeFor(device: PodDevice): BatteryEstimate? = takeIf {
    device.batteryEstimateEnabled && device.isLive && device.profileId != null
}

/**
 * Looks up and gates the estimate to show for [device]. Shared by all surfaces (dashboard,
 * widget, notification) so the display rules can't drift apart.
 */
fun Map<String, BatteryEstimate>.estimateFor(device: PodDevice): BatteryEstimate? =
    device.profileId?.let { this[it] }?.takeFor(device)

/**
 * Minutes to display for one pod: the time-until-full while [charging] (null when no usable
 * charge ETA exists, e.g. during an Optimized Battery Charging hold), otherwise the runtime
 * projection. [charging] should be the same flag that drives the visible charging indicator,
 * so the shown duration never contradicts the bolt icon next to it.
 */
fun BatteryEstimate.Pod.displayMinutes(charging: Boolean): Int? = when {
    charging -> minutesUntilCharged
    else -> minutesRemaining
}

/**
 * The per-slot minutes a surface would actually render for [device], or null when no slot shows
 * anything. Everything else on the estimate (rates, source) churns without visible effect —
 * dedupe render triggers on this key so invisible changes don't refresh widgets/notifications.
 */
fun BatteryEstimate.displayKey(device: PodDevice): List<Int?>? = listOf(
    left?.displayMinutes(device.isLeftPodCharging == true),
    right?.displayMinutes(device.isRightPodCharging == true),
    headset?.displayMinutes(device.isHeadsetBeingCharged == true),
).takeUnless { key -> key.all { it == null } }
