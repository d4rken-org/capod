package eu.darken.capod.monitor.core.battery

import eu.darken.capod.monitor.core.BatteryReading
import eu.darken.capod.pods.core.apple.PodModel
import kotlin.math.floor

/** How many more times the case can recharge both earbuds, and how firm that number is. */
data class CaseCharges(
    val count: Int,
    val display: Display,
    val adequacy: Adequacy,
) {
    enum class Display {
        /** Nothing left to give — distinct from [LESS_THAN_ONE], which still implies some charge. */
        EMPTY,
        LESS_THAN_ONE,
        AT_LEAST,
        APPROXIMATE,
    }

    enum class Adequacy { ENOUGH, NOT_ENOUGH, UNCERTAIN }
}

/**
 * Reads a truncating [reading] as the interval `[percent, percent + resolution)` and scales it by
 * the spec, so a decile source at 20% asks "is 4.0 x 0.20 .. 4.0 x 0.30 above one charge?" rather
 * than pretending 0.8 is exact. Only an interval that lies wholly on one side of a full charge
 * makes a claim; anything straddling it is [CaseCharges.Adequacy.UNCERTAIN]. That also absorbs the
 * bounce between adjacent readings, so no state has to be remembered between frames.
 *
 * The interval excludes its upper end, so an upper end of exactly one charge is still short of one.
 *
 * A spec that is itself a lower bound has no upper end, so it can never claim "not enough".
 *
 * Null when the model publishes no case figures or the reading is missing or nonsense — the caller
 * shows no line at all rather than a neutral one.
 */
fun caseCharges(spec: PodModel.CaseSpec?, reading: BatteryReading?): CaseCharges? {
    if (spec == null || !spec.fullPairRecharges.isFinite() || spec.fullPairRecharges <= 0f) return null
    if (reading == null || !reading.percent.isFinite() || reading.percent < 0f) return null

    val resolution = if (reading.resolution.isFinite() && reading.resolution > 0f) reading.resolution else 0f
    val lowest = spec.fullPairRecharges * reading.percent
    val highest = spec.fullPairRecharges * (reading.percent + resolution)

    val adequacy = when {
        lowest >= 1f -> CaseCharges.Adequacy.ENOUGH
        spec.isLowerBound -> CaseCharges.Adequacy.UNCERTAIN
        highest <= 1f -> CaseCharges.Adequacy.NOT_ENOUGH
        else -> CaseCharges.Adequacy.UNCERTAIN
    }

    val count = floor(lowest).toInt()
    val display = when {
        reading.percent == 0f -> CaseCharges.Display.EMPTY
        count < 1 -> CaseCharges.Display.LESS_THAN_ONE
        spec.isLowerBound -> CaseCharges.Display.AT_LEAST
        else -> CaseCharges.Display.APPROXIMATE
    }

    return CaseCharges(count = count, display = display, adequacy = adequacy)
}
