package eu.darken.capod.monitor.core.battery

import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import kotlin.math.roundToInt

/**
 * Derives a rough per-pod battery-health percentage from learned drain rates vs the model's rated
 * battery life. Nothing on the wire exposes Apple's real health/cycle data, so this is a usage-based
 * proxy: a pod that only lasts 4.5h of a rated 6h reads as ~75%.
 *
 * Health is computed PER POD — single-pod listening habits or a replaced earbud make the two sides
 * genuinely diverge, and a combined figure would mask a failing pod. Only [DrainProfile.listeningRates]
 * feed it (segments where the pod was worn AND audio was playing on this device), because Apple's
 * ratings are listening figures — general rates include idle wear and would flatter health. Within a
 * pod, the MEDIAN of its qualifying rates is used rather than the best or worst, damping remaining
 * confounds (volume, calls, cold) in either direction. It remains an estimate — label it as such in
 * the UI.
 */
object BatteryHealth {

    /** A learned rate must have accumulated this many separate sessions before it counts. */
    const val MIN_UPDATE_COUNT = 3

    data class PerPod(
        val left: Int? = null,
        val right: Int? = null,
        val headset: Int? = null,
    ) {
        val hasAny: Boolean get() = left != null || right != null || headset != null
    }

    fun estimate(profile: DrainProfile?, model: PodModel): PerPod? {
        if (profile == null) return null
        val spec = model.batterySpec ?: return null
        if (!profile.matchesModel(model)) return null

        return PerPod(
            left = slotPercent(profile, spec, "LEFT"),
            right = slotPercent(profile, spec, "RIGHT"),
            headset = slotPercent(profile, spec, "HEADSET"),
        ).takeIf { it.hasAny }
    }

    private fun slotPercent(profile: DrainProfile, spec: PodModel.BatterySpec, slot: String): Int? {
        val ratios = profile.listeningRates.mapNotNull { (key, rate) ->
            // Keys must be exactly "<bucket>/<slot>" — anything else is corrupted or
            // future-format data and must not feed a health figure.
            val parts = key.split('/')
            if (parts.size != 2 || parts[1] != slot) return@mapNotNull null
            val specHours = specHoursFor(spec, parts[0]) ?: return@mapNotNull null
            if (rate.updateCount < MIN_UPDATE_COUNT) return@mapNotNull null
            if (!rate.fractionPerHour.isFinite() || rate.fractionPerHour <= 0f) return@mapNotNull null
            (1f / specHours) / rate.fractionPerHour
        }
        if (ratios.isEmpty()) return null

        val sorted = ratios.sorted()
        val median = if (sorted.size % 2 == 1) {
            sorted[sorted.size / 2]
        } else {
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2f
        }
        return (median * 100f).roundToInt().coerceIn(1, 100)
    }

    /**
     * The rated hours a rate learned in [bucket] should be judged against. UNKNOWN-bucket usage
     * can't be matched to a specific mode, so it's compared to the middle of the two ratings —
     * the shorter one would systematically flatter health, the longer one would slander it.
     * Malformed or unrecognized bucket keys yield null (entry is skipped).
     */
    private fun specHoursFor(spec: PodModel.BatterySpec, bucket: String): Float? {
        val on = spec.listeningHoursAncOn
        val off = spec.listeningHoursAncOff
        return when (bucket) {
            AapSetting.AncMode.Value.OFF.name -> off ?: on
            AapSetting.AncMode.Value.ON.name,
            AapSetting.AncMode.Value.TRANSPARENCY.name,
            AapSetting.AncMode.Value.ADAPTIVE.name,
            -> on ?: off
            DrainProfile.BUCKET_UNKNOWN -> listOfNotNull(on, off).takeIf { it.isNotEmpty() }?.average()?.toFloat()
            else -> null
        }?.takeIf { it.isFinite() && it > 0f }
    }
}
