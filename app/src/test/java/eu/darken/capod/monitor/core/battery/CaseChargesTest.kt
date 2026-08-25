package eu.darken.capod.monitor.core.battery

import eu.darken.capod.monitor.core.BatteryReading
import eu.darken.capod.pods.core.apple.PodModel
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class CaseChargesTest : BaseTest() {

    private val exact = PodModel.CaseSpec(fullPairRecharges = 4.0f)
    private val lowerBound = PodModel.CaseSpec(fullPairRecharges = 3.8f, isLowerBound = true)

    private val decile = 0.10f
    private val percent = 0.01f

    private fun charges(spec: PodModel.CaseSpec?, percent: Float, resolution: Float) =
        caseCharges(spec, BatteryReading(percent, resolution))

    @Test
    fun `no spec means no line`() {
        caseCharges(null, BatteryReading(0.5f, percent)).shouldBeNull()
    }

    @Test
    fun `no reading means no line`() {
        caseCharges(exact, null).shouldBeNull()
    }

    @Test
    fun `an unusable spec is treated as absent`() {
        charges(PodModel.CaseSpec(fullPairRecharges = 0f), 0.5f, percent).shouldBeNull()
        charges(PodModel.CaseSpec(fullPairRecharges = -1f), 0.5f, percent).shouldBeNull()
        charges(PodModel.CaseSpec(fullPairRecharges = Float.NaN), 0.5f, percent).shouldBeNull()
    }

    @Test
    fun `an unusable reading is treated as absent`() {
        charges(exact, -1f, percent).shouldBeNull()
        charges(exact, Float.NaN, percent).shouldBeNull()
        charges(exact, Float.POSITIVE_INFINITY, percent).shouldBeNull()
    }

    @Test
    fun `the count comes from the spec times the reading`() {
        charges(exact, 1.0f, percent)!!.count shouldBe 4
        charges(exact, 0.75f, percent)!!.count shouldBe 3
        charges(exact, 0.50f, percent)!!.count shouldBe 2
        charges(lowerBound, 1.0f, percent)!!.count shouldBe 3
    }

    @Test
    fun `a whole interval above one charge is enough`() {
        // 4.0 x 0.25 = 1.0 exactly, so even the low end clears a full charge
        charges(exact, 0.25f, decile)!!.adequacy shouldBe CaseCharges.Adequacy.ENOUGH
        charges(exact, 0.60f, percent)!!.adequacy shouldBe CaseCharges.Adequacy.ENOUGH
    }

    @Test
    fun `a whole interval below one charge is not enough`() {
        // 4.0 x (0.10 + 0.10) = 0.8, still short of a full charge at the top of the interval
        charges(exact, 0.10f, decile)!!.adequacy shouldBe CaseCharges.Adequacy.NOT_ENOUGH
        charges(exact, 0.20f, percent)!!.adequacy shouldBe CaseCharges.Adequacy.NOT_ENOUGH
    }

    @Test
    fun `an interval ending exactly on one charge is not enough`() {
        // the top of the interval is excluded, so 5.0 x (0.10 + 0.10) = 1.0 is still short
        charges(PodModel.CaseSpec(fullPairRecharges = 5.0f), 0.10f, decile)!!
            .adequacy shouldBe CaseCharges.Adequacy.NOT_ENOUGH
        // 4.0 x (0.24 + 0.01) = 1.0
        charges(exact, 0.24f, percent)!!.adequacy shouldBe CaseCharges.Adequacy.NOT_ENOUGH
    }

    @Test
    fun `an interval straddling one charge claims nothing`() {
        // 4.0 x 0.20 = 0.8, 4.0 x 0.30 = 1.2
        charges(exact, 0.20f, decile)!!.adequacy shouldBe CaseCharges.Adequacy.UNCERTAIN
        // the same reading at a finer resolution does resolve
        charges(exact, 0.20f, percent)!!.adequacy shouldBe CaseCharges.Adequacy.NOT_ENOUGH
    }

    @Test
    fun `a lower bound spec above empty never claims not enough`() {
        // 3.8 x 0.25 = 0.95 — a confident "not enough" for a case that may well hold more
        charges(lowerBound, 0.25f, percent)!!.adequacy shouldBe CaseCharges.Adequacy.UNCERTAIN
        charges(lowerBound, 0.01f, percent)!!.adequacy shouldBe CaseCharges.Adequacy.UNCERTAIN
        charges(lowerBound, 0.10f, decile)!!.adequacy shouldBe CaseCharges.Adequacy.UNCERTAIN
    }

    @Test
    fun `a lower bound spec can still claim enough`() {
        charges(lowerBound, 0.30f, percent)!!.adequacy shouldBe CaseCharges.Adequacy.ENOUGH
    }

    @Test
    fun `an exact spec renders as an approximation`() {
        charges(exact, 0.50f, percent)!!.display shouldBe CaseCharges.Display.APPROXIMATE
    }

    @Test
    fun `a lower bound spec renders as a floor`() {
        charges(lowerBound, 0.50f, percent)!!.display shouldBe CaseCharges.Display.AT_LEAST
    }

    @Test
    fun `below a full charge renders as less than one`() {
        charges(exact, 0.20f, percent)!!.display shouldBe CaseCharges.Display.LESS_THAN_ONE
        charges(exact, 0.01f, percent)!!.display shouldBe CaseCharges.Display.LESS_THAN_ONE
    }

    @Test
    fun `an interval straddling one charge rounds to one instead of claiming less`() {
        // 4.0 x 0.20 = 0.8, 4.0 x 0.30 = 1.2 — the text must not deny what the interval allows
        val straddle = charges(exact, 0.20f, decile)!!
        straddle.display shouldBe CaseCharges.Display.APPROXIMATE
        straddle.count shouldBe 1
    }

    @Test
    fun `an uncertain lower bound spec names no number`() {
        // 3.8 x 0.10 = 0.38 with no upper end, so neither "less than one" nor "~1" is supportable
        val vague = charges(lowerBound, 0.10f, decile)!!
        vague.adequacy shouldBe CaseCharges.Adequacy.UNCERTAIN
        vague.display shouldBe CaseCharges.Display.UNCERTAIN

        charges(lowerBound, 0.20f, percent)!!.display shouldBe CaseCharges.Display.UNCERTAIN
    }

    @Test
    fun `a lower bound spec that reaches enough still counts`() {
        val enough = charges(lowerBound, 0.50f, decile)!!
        enough.adequacy shouldBe CaseCharges.Adequacy.ENOUGH
        enough.display shouldBe CaseCharges.Display.AT_LEAST
        // 3.8 x 0.50 = 1.9
        enough.count shouldBe 1
    }

    @Test
    fun `an empty case renders as empty and claims not enough`() {
        val exactly = charges(exact, 0f, percent)!!
        exactly.display shouldBe CaseCharges.Display.EMPTY
        exactly.adequacy shouldBe CaseCharges.Adequacy.NOT_ENOUGH

        // an empty case holds nothing for the open ended spec to undersell
        val floored = charges(lowerBound, 0f, decile)!!
        floored.display shouldBe CaseCharges.Display.EMPTY
        floored.adequacy shouldBe CaseCharges.Adequacy.NOT_ENOUGH
    }
}
