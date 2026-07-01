package eu.darken.capod.monitor.core.battery

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A single battery observation for one slot (left / right / headset).
 *
 * [atElapsedMs] is a monotonic timestamp ([eu.darken.capod.common.TimeSource.elapsedRealtime]) so
 * wall-clock jumps can't corrupt the regression. [fraction] is a battery level in `0.0..1.0`
 * (the same unit used everywhere else in the app), NOT a 0..100 percentage.
 */
data class DrainSample(
    val atElapsedMs: Long,
    val fraction: Float,
)

/**
 * Pure, side-effect-free drain-rate math. Kept separate from [BatteryEstimator] so the numerics can
 * be unit-tested without Android, coroutines, or persistence.
 *
 * All rates are **fraction per hour** (e.g. `0.169` = 16.9 %/hr) to match the `0.0..1.0` battery unit.
 * Mixing this up with a 0..100 percentage would produce a 100× error, so the unit is in the name.
 */
object DrainModel {

    /** Minimum samples before a live regression is trusted. */
    const val MIN_SAMPLES = 4

    /** Minimum span between oldest and newest sample. Rejects bursts of rapid 1% ticks. */
    const val MIN_SPAN_MS = 3 * 60_000L

    /** Minimum total drop across the window. Rejects noise that isn't a real discharge. */
    const val MIN_TOTAL_DROP = 0.03f

    /**
     * Only samples within this window of the newest one feed the regression. Drops stale points
     * from before a long gap (out of range / overnight) so a reconnect can't fit a line across
     * hours of absence.
     */
    const val MAX_SAMPLE_AGE_MS = 2 * 60 * 60_000L

    /** Plausible drain-rate band (fraction/hour). Outside this, the estimate is rejected. */
    const val RATE_MIN = 0.02f
    const val RATE_MAX = 0.80f

    /** Estimates above this are implausible and suppressed. */
    const val MAX_MINUTES = 24 * 60

    /**
     * Smoothing for the displayed minutes. Asymmetric on purpose: react quickly when the estimate
     * DROPS (less time left — e.g. a degraded battery measured draining faster than its rating, or a
     * first live fit undercutting the spec seed) so we don't keep showing more life than the latest
     * reading supports, but ease UP slowly to swallow upward noise and stay conservative.
     */
    const val MINUTES_ALPHA_DOWN = 0.6f
    const val MINUTES_ALPHA_UP = 0.25f

    /** Smoothing for the persisted per-mode learned rate. */
    const val LEARN_ALPHA = 0.3f

    /**
     * Least-squares slope of [samples] (fraction vs. hours) as a positive drain rate in
     * fraction/hour, or null if there aren't enough samples, the window is too short/small, the
     * battery isn't actually draining, or the result is outside [RATE_MIN]..[RATE_MAX].
     */
    fun slopeFractionPerHour(samples: List<DrainSample>): Float? {
        if (samples.size < MIN_SAMPLES) return null

        // Restrict to the recent window so a gap before the newest sample can't span the fit.
        val newestMs = samples.last().atElapsedMs
        val recent = samples.filter { newestMs - it.atElapsedMs <= MAX_SAMPLE_AGE_MS }
        if (recent.size < MIN_SAMPLES) return null

        val first = recent.first()
        val last = recent.last()
        if (last.atElapsedMs - first.atElapsedMs < MIN_SPAN_MS) return null
        if (first.fraction - last.fraction < MIN_TOTAL_DROP) return null

        val n = recent.size.toDouble()
        var sumX = 0.0
        var sumY = 0.0
        var sumXY = 0.0
        var sumXX = 0.0
        for (s in recent) {
            val x = (s.atElapsedMs - first.atElapsedMs) / 3_600_000.0 // hours since first
            val y = s.fraction.toDouble()
            sumX += x
            sumY += y
            sumXY += x * y
            sumXX += x * x
        }
        val denominator = n * sumXX - sumX * sumX
        if (abs(denominator) < 1e-9) return null

        // Negative slope == draining; flip to a positive drain rate.
        val rate = (-((n * sumXY - sumX * sumY) / denominator)).toFloat()
        if (!rate.isFinite() || rate < RATE_MIN || rate > RATE_MAX) return null
        return rate
    }

    /**
     * Minutes until [levelFraction] reaches empty at [fractionPerHour], or null if the rate is
     * non-positive or the result is implausible (<=0 or above [MAX_MINUTES]).
     */
    fun minutesRemaining(levelFraction: Float, fractionPerHour: Float): Int? {
        if (fractionPerHour <= 0f || !levelFraction.isFinite() || levelFraction <= 0f) return null
        val minutes = (levelFraction / fractionPerHour * 60.0).roundToInt()
        return minutes.takeIf { it in 1..MAX_MINUTES }
    }

    /**
     * Exponential moving average over the displayed minutes, to avoid a jumpy number. Uses the
     * asymmetric [MINUTES_ALPHA_DOWN]/[MINUTES_ALPHA_UP] factors by default; pass an explicit [alpha]
     * to force a symmetric factor (used by tests).
     */
    fun blendMinutes(previous: Int?, next: Int, alpha: Float? = null): Int {
        if (previous == null) return next
        val a = alpha ?: if (next < previous) MINUTES_ALPHA_DOWN else MINUTES_ALPHA_UP
        return (next * a + previous * (1f - a)).roundToInt()
    }

    /** Exponential moving average over the persisted learned rate across sessions. */
    fun blendRate(previous: Float?, next: Float, alpha: Float = LEARN_ALPHA): Float =
        if (previous == null) next else next * alpha + previous * (1f - alpha)
}
