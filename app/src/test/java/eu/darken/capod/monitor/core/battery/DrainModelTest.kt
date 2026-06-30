package eu.darken.capod.monitor.core.battery

import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class DrainModelTest : BaseTest() {

    /** Samples draining at a constant rate, [perMinute] fraction lost per minute. */
    private fun drainingSamples(
        start: Float,
        perMinute: Float,
        count: Int,
        stepMinutes: Long = 4,
    ): List<DrainSample> = (0 until count).map { i ->
        DrainSample(
            atElapsedMs = i * stepMinutes * 60_000L,
            fraction = start - perMinute * (i * stepMinutes),
        )
    }

    @Test
    fun `slope recovers a constant drain rate in fraction per hour`() {
        // 0.25% per minute == 15% per hour == 0.15 fraction/hour.
        val rate = DrainModel.slopeFractionPerHour(drainingSamples(0.80f, 0.0025f, count = 6))
        rate.shouldNotBeNull()
        rate shouldBe (0.15f plusOrMinus 0.01f)
    }

    @Test
    fun `rate is a fraction not a percentage`() {
        // Sanity guard against a 100x unit error: a ~15%/hr drain must be ~0.15, never ~15.
        val rate = DrainModel.slopeFractionPerHour(drainingSamples(0.80f, 0.0025f, count = 6))!!
        (rate < 1f) shouldBe true
    }

    @Test
    fun `too few samples yields null`() {
        DrainModel.slopeFractionPerHour(drainingSamples(0.80f, 0.0025f, count = 3)).shouldBeNull()
    }

    @Test
    fun `a too-short window is rejected`() {
        // 4 samples 30s apart: enough points, but span < MIN_SPAN_MS.
        val samples = (0 until 4).map { DrainSample(it * 30_000L, 0.80f - it * 0.01f) }
        DrainModel.slopeFractionPerHour(samples).shouldBeNull()
    }

    @Test
    fun `a negligible drop is rejected`() {
        // Long enough window but total drop below MIN_TOTAL_DROP.
        val samples = (0 until 5).map { DrainSample(it * 5 * 60_000L, 0.80f - it * 0.002f) }
        DrainModel.slopeFractionPerHour(samples).shouldBeNull()
    }

    @Test
    fun `a charging-style increase is not a drain`() {
        val samples = (0 until 5).map { DrainSample(it * 4 * 60_000L, 0.50f + it * 0.02f) }
        DrainModel.slopeFractionPerHour(samples).shouldBeNull()
    }

    @Test
    fun `an implausibly fast drain is rejected`() {
        // 4 rapid 1% ticks spanning > MIN_SPAN but dropping far too fast (~120%/hr).
        val samples = (0 until 5).map { DrainSample(it * 60_000L, 0.80f - it * 0.02f) }
            .let { it + DrainSample(it.size * 60_000L, 0.70f) } // keep span > 3 min
        DrainModel.slopeFractionPerHour(samples).shouldBeNull()
    }

    @Test
    fun `minutesRemaining divides level by rate`() {
        // 50% left at 0.15/hr -> 0.5 / 0.15 * 60 = 200 minutes.
        DrainModel.minutesRemaining(0.50f, 0.15f) shouldBe 200
    }

    @Test
    fun `minutesRemaining rejects a non-positive rate`() {
        DrainModel.minutesRemaining(0.50f, 0f).shouldBeNull()
    }

    @Test
    fun `minutesRemaining suppresses absurd estimates`() {
        // Extremely slow rate -> beyond MAX_MINUTES -> suppressed.
        DrainModel.minutesRemaining(1.0f, 0.0001f).shouldBeNull()
    }

    @Test
    fun `blendMinutes seeds then smooths`() {
        DrainModel.blendMinutes(previous = null, next = 100) shouldBe 100
        // 0.3 * 200 + 0.7 * 100 = 130
        DrainModel.blendMinutes(previous = 100, next = 200, alpha = 0.3f) shouldBe 130
    }

    @Test
    fun `blendRate seeds then smooths`() {
        DrainModel.blendRate(previous = null, next = 0.2f) shouldBe 0.2f
        DrainModel.blendRate(previous = 0.1f, next = 0.2f, alpha = 0.3f) shouldBe (0.13f plusOrMinus 0.0001f)
    }

    @Test
    fun `regression denominator is well conditioned for spread samples`() {
        // Guard that real spread input produces a finite, positive rate (no divide-by-zero path).
        val rate = DrainModel.slopeFractionPerHour(drainingSamples(0.90f, 0.003f, count = 8))!!
        (rate.isFinite() && rate > 0f) shouldBe true
    }
}
