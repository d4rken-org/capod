package eu.darken.capod.monitor.core.battery

import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

/**
 * Derives how much of a model's rated listening time a pod still delivers, from learned drain rates
 * vs that rating: a pod that only lasts 4.5h of a rated 6h reads as ~75%. Nothing on the wire
 * exposes Apple's real health/cycle data, and a short runtime can just as well come from loud
 * volume, cold weather or a hungry codec — so this is a runtime figure, not a cell-health verdict.
 *
 * It is computed PER POD — single-pod listening habits or a replaced earbud make the two sides
 * genuinely diverge, and a combined figure would mask a failing pod. Only [DrainProfile.listeningRates]
 * feed it (segments where the pod was worn AND audio was playing on this device), because Apple's
 * ratings are listening figures — general rates include idle wear and would flatter the result.
 * Within a pod, the MEDIAN of its qualifying rates is used rather than the best or worst, damping
 * remaining confounds (volume, calls, cold) in either direction. It remains an estimate — label it
 * as such in the UI.
 *
 * A figure that is merely displayed needs less backing than one that raises a warning, hence
 * [Reading.isPromotable].
 */
object BatteryHealth {

    /** A learned rate must have accumulated this many separate sessions before it counts. */
    const val MIN_UPDATE_COUNT = 3

    /**
     * Sessions a slot must have accumulated across all its qualifying rates before its figure may
     * be promoted into a warning. `updateCount` rises once per listening session, so this is
     * roughly a week of real use.
     */
    const val MIN_PROMOTE_UPDATE_COUNT = 8

    /** A slot's newest qualifying rate may be at most this old, or its figure is too stale to warn on. */
    val MAX_PROMOTE_AGE: Duration = Duration.ofDays(60)

    /** At or below this percentage of the rated listening time, a promotable reading warrants a warning. */
    const val LOW_RUNTIME_PERCENT = 50

    enum class Slot { LEFT, RIGHT, HEADSET }

    /**
     * @param percent share of the rated listening time this pod still reaches, 1..100
     * @param isPromotable whether enough recent evidence backs [percent] to act on it
     */
    data class Reading(
        val percent: Int,
        val isPromotable: Boolean,
    )

    data class SlotReading(
        val slot: Slot,
        val reading: Reading,
    )

    data class PerPod(
        val left: Reading? = null,
        val right: Reading? = null,
        val headset: Reading? = null,
    ) {
        val hasAny: Boolean get() = left != null || right != null || headset != null

        /** The worst reading that rests on enough recent evidence to act on, or null if none does. */
        val lowestPromotable: SlotReading?
            get() = listOfNotNull(
                left?.let { SlotReading(Slot.LEFT, it) },
                right?.let { SlotReading(Slot.RIGHT, it) },
                headset?.let { SlotReading(Slot.HEADSET, it) },
            )
                .filter { it.reading.isPromotable }
                .minByOrNull { it.reading.percent }
    }

    fun estimate(profile: DrainProfile?, model: PodModel, now: Instant): PerPod? {
        if (profile == null) return null
        val spec = model.batterySpec ?: return null
        if (!profile.matchesModel(model)) return null

        return PerPod(
            left = slotReading(profile, spec, Slot.LEFT, now),
            right = slotReading(profile, spec, Slot.RIGHT, now),
            headset = slotReading(profile, spec, Slot.HEADSET, now),
        ).takeIf { it.hasAny }
    }

    private fun slotReading(
        profile: DrainProfile,
        spec: PodModel.BatterySpec,
        slot: Slot,
        now: Instant,
    ): Reading? {
        val qualifying = profile.listeningRates.mapNotNull { (key, rate) ->
            // Keys must be exactly "<bucket>/<slot>" — anything else is corrupted or
            // future-format data and must not feed a runtime figure.
            val parts = key.split('/')
            if (parts.size != 2 || parts[1] != slot.name) return@mapNotNull null
            val specHours = specHoursFor(spec, parts[0]) ?: return@mapNotNull null
            if (rate.updateCount < MIN_UPDATE_COUNT) return@mapNotNull null
            if (!rate.fractionPerHour.isFinite() || rate.fractionPerHour <= 0f) return@mapNotNull null
            (1f / specHours) / rate.fractionPerHour to rate
        }
        if (qualifying.isEmpty()) return null

        val sorted = qualifying.map { it.first }.sorted()
        val median = if (sorted.size % 2 == 1) {
            sorted[sorted.size / 2]
        } else {
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2f
        }

        val sessions = qualifying.sumOf { it.second.updateCount }
        val newest = qualifying.maxOf { it.second.updatedAt }
        return Reading(
            percent = (median * 100f).roundToInt().coerceIn(1, 100),
            isPromotable = sessions >= MIN_PROMOTE_UPDATE_COUNT &&
                Duration.between(newest, now) <= MAX_PROMOTE_AGE,
        )
    }

    /**
     * The rated hours a rate learned in [bucket] should be judged against. UNKNOWN-bucket usage
     * can't be matched to a specific mode, so it's compared to the middle of the two ratings —
     * the shorter one would systematically flatter the figure, the longer one would slander it.
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
