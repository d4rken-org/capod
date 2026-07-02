package eu.darken.capod.monitor.core.battery

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

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

    /**
     * Charge fits get by with fewer samples than drain fits: a charge session is short (well under
     * an hour) and BLE's 10% steps would otherwise need most of the session before a fit exists.
     */
    const val MIN_SAMPLES_CHARGE = 3

    /** Minimum total rise across the charge window — rejects jitter that isn't a real charge. */
    const val MIN_TOTAL_RISE = 0.05f

    /** Plausible charge-rate band (fraction/hour): a full charge in 15 minutes .. 4 hours. */
    const val CHARGE_RATE_MIN = 0.25f
    const val CHARGE_RATE_MAX = 4.0f

    /**
     * Above this level the "until charged" estimate is suppressed. The trickle band models the slow
     * tail, so suppression only covers the last sliver where the firmware is about to flip the
     * charging flag off at 100% anyway.
     */
    const val NEAR_FULL_SUPPRESS = 0.99f

    /** Narrow bands (10% wide) accept a two-point fit — a full BLE band is exactly two ticks. */
    const val MIN_SAMPLES_CHARGE_NARROW = 2

    /** Minimum rise WITHIN a band before its fit is trusted (half a narrow band). */
    const val MIN_BAND_RISE = 0.05f

    /** [chargeStallThresholdMs] never goes below this, however fast the rate claims to be. */
    const val CHARGE_STALL_FLOOR_MS = 10 * 60_000L

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
        val recent = recentWindow(samples, MIN_SAMPLES) ?: return null
        if (recent.first().fraction - recent.last().fraction < MIN_TOTAL_DROP) return null

        // Negative slope == draining; flip to a positive drain rate.
        val rate = regressionSlopePerHour(recent)?.let { -it } ?: return null
        if (!rate.isFinite() || rate < RATE_MIN || rate > RATE_MAX) return null
        return rate
    }

    /**
     * Least-squares slope of RISING [samples] (a charging pod) as a positive charge rate in
     * fraction/hour, with the same window guards as the drain fit but charge-tuned thresholds.
     */
    fun chargeSlopeFractionPerHour(samples: List<DrainSample>): Float? {
        val recent = recentWindow(samples, MIN_SAMPLES_CHARGE) ?: return null
        if (recent.last().fraction - recent.first().fraction < MIN_TOTAL_RISE) return null

        val rate = regressionSlopePerHour(recent) ?: return null
        if (!rate.isFinite() || rate < CHARGE_RATE_MIN || rate > CHARGE_RATE_MAX) return null
        return rate
    }

    /**
     * The three regimes of a lithium charge. Constant-current bulk is fast and roughly linear;
     * above ~80% the charger switches to constant-voltage and the intake tapers, ending in a slow
     * trickle. One linear rate over-promises badly above 80%, so each band learns its own rate.
     *
     * [specMultiplier] scales the spec-derived seed for the band: Apple's quick-charge claims
     * ("5 minutes = ~1 hour of listening") measure the bulk phase, so seeding the taper/trickle
     * bands from them needs a haircut. Applied ONLY to the spec seed — measured or learned rates
     * already reflect where they were observed.
     */
    enum class ChargeBand(val from: Float, val to: Float, val specMultiplier: Float) {
        BULK(0.0f, 0.8f, 1.0f),
        TAPER(0.8f, 0.9f, 0.5f),
        TRICKLE(0.9f, 1.0f, 0.3f),
    }

    /**
     * Least-squares charge rate fitted ONLY to the recent samples inside [band], or null when the
     * band lacks coverage. Narrow bands accept two points (a full BLE band is exactly two ticks);
     * the wide bulk band keeps the regular sample requirement.
     */
    fun chargeBandSlopeFractionPerHour(samples: List<DrainSample>, band: ChargeBand): Float? {
        val newestMs = samples.lastOrNull()?.atElapsedMs ?: return null
        val recent = samples.filter {
            newestMs - it.atElapsedMs <= MAX_SAMPLE_AGE_MS && it.fraction in band.from..band.to
        }
        val minSamples = if (band == ChargeBand.BULK) MIN_SAMPLES_CHARGE else MIN_SAMPLES_CHARGE_NARROW
        if (recent.size < minSamples) return null
        if (recent.last().atElapsedMs - recent.first().atElapsedMs < MIN_SPAN_MS) return null
        if (recent.last().fraction - recent.first().fraction < MIN_BAND_RISE) return null

        val rate = regressionSlopePerHour(recent) ?: return null
        // The taper/trickle bands are legitimately slower than any plausible bulk rate.
        val floor = CHARGE_RATE_MIN * band.specMultiplier
        return rate.takeIf { it.isFinite() && it >= floor && it <= CHARGE_RATE_MAX }
    }

    /**
     * Minutes until [levelFraction] reaches full, walking the remaining [ChargeBand]s at
     * [rateForBand]'s per-band rates (partial current band + all bands above it). Null when the
     * level is already in the suppression sliver ([NEAR_FULL_SUPPRESS]), any needed band has no
     * usable rate, or the result is implausible.
     */
    fun minutesUntilFull(levelFraction: Float, rateForBand: (ChargeBand) -> Float?): Int? {
        if (!levelFraction.isFinite() || levelFraction < 0f) return null
        if (levelFraction >= NEAR_FULL_SUPPRESS) return null

        var totalMinutes = 0.0
        for (band in ChargeBand.entries) {
            val start = maxOf(levelFraction, band.from)
            val missing = band.to - start
            if (missing <= 0f) continue
            val rate = rateForBand(band) ?: return null
            if (rate <= 0f) return null
            totalMinutes += missing / rate * 60.0
        }
        return totalMinutes.roundToInt().takeIf { it in 1..MAX_MINUTES }
    }

    /**
     * How long the level may sit unchanged while charging before the "until charged" estimate is
     * considered stalled (Optimized Battery Charging hold, trickle phase, or a dead reading) and
     * suppressed. Granularity-aware: at [chargeFractionPerHour] a single visible step of
     * [stepFraction] (1% on AAP, 10% on BLE) takes `step/rate` hours — a slow BLE charge legitimately
     * shows no change for ~20 minutes, so a fixed timeout would falsely suppress it.
     */
    fun chargeStallThresholdMs(chargeFractionPerHour: Float, stepFraction: Float): Long {
        if (chargeFractionPerHour <= 0f) return CHARGE_STALL_FLOOR_MS
        val stepMs = (stepFraction.toDouble() / chargeFractionPerHour * 3_600_000).roundToLong()
        return maxOf(CHARGE_STALL_FLOOR_MS, stepMs * 3 / 2)
    }

    /** Samples within [MAX_SAMPLE_AGE_MS] of the newest, or null when count/span guards fail. */
    private fun recentWindow(samples: List<DrainSample>, minSamples: Int): List<DrainSample>? {
        if (samples.size < minSamples) return null

        // Restrict to the recent window so a gap before the newest sample can't span the fit.
        val newestMs = samples.last().atElapsedMs
        val recent = samples.filter { newestMs - it.atElapsedMs <= MAX_SAMPLE_AGE_MS }
        if (recent.size < minSamples) return null
        if (recent.last().atElapsedMs - recent.first().atElapsedMs < MIN_SPAN_MS) return null
        return recent
    }

    /** Signed least-squares slope (fraction per hour) over [recent]; negative when draining. */
    private fun regressionSlopePerHour(recent: List<DrainSample>): Float? {
        val first = recent.first()
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
        return ((n * sumXY - sumX * sumY) / denominator).toFloat()
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
