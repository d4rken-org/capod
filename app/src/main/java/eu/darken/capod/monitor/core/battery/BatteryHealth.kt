package eu.darken.capod.monitor.core.battery

import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import kotlin.math.roundToInt

/**
 * Derives a rough battery-health percentage from learned drain rates vs the model's rated battery
 * life. Nothing on the wire exposes Apple's real health/cycle data, so this is a usage-based proxy:
 * a battery that only lasts 4.5h of a rated 6h reads as ~75%.
 *
 * The MEDIAN of the qualifying learned rates is used rather than the best or worst: sessions where
 * the pods idled (in-ear, nothing playing) drain slower than the listening rating and would pull a
 * "best" pick to a meaningless 100%, while call-heavy or cold sessions drain faster and would drag
 * a "worst" pick into false doom. The median lands between both confounds. It remains an estimate —
 * label it as such in the UI.
 */
object BatteryHealth {

    /** A learned rate must have accumulated this many separate sessions before it counts. */
    const val MIN_UPDATE_COUNT = 3

    private val VALID_SLOTS = setOf("LEFT", "RIGHT", "HEADSET")

    fun estimatePercent(profile: DrainProfile?, model: PodModel): Int? {
        if (profile == null) return null
        val spec = model.batterySpec ?: return null
        if (!profile.matchesModel(model)) return null

        val ratios = profile.rates.mapNotNull { (key, rate) ->
            // Keys must be exactly "<bucket>/<slot>" with a known slot — anything else is corrupted
            // or future-format data and must not feed a health figure.
            val parts = key.split('/')
            if (parts.size != 2 || parts[1] !in VALID_SLOTS) return@mapNotNull null
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
