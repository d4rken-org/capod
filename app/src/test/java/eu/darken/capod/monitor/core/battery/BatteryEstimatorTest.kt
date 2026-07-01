package eu.darken.capod.monitor.core.battery

import eu.darken.capod.common.TimeSource
import eu.darken.capod.monitor.core.DeviceMonitor
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.AapPodState
import eu.darken.capod.pods.core.apple.aap.AapPodState.Battery
import eu.darken.capod.pods.core.apple.aap.AapPodState.BatteryType
import eu.darken.capod.pods.core.apple.aap.AapPodState.ChargingState
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant

class BatteryEstimatorTest : BaseTest() {

    private val now = Instant.parse("2026-04-02T12:00:00Z")

    private fun device(
        profileId: String?,
        left: Float?,
        right: Float?,
        charging: Boolean = false,
        model: PodModel? = null,
        estimateEnabled: Boolean = true,
    ): PodDevice {
        val state = if (charging) ChargingState.CHARGING else ChargingState.NOT_CHARGING
        val batteries = buildMap {
            if (left != null) put(BatteryType.LEFT, Battery(BatteryType.LEFT, left, state))
            if (right != null) put(BatteryType.RIGHT, Battery(BatteryType.RIGHT, right, state))
        }
        return PodDevice(
            profileId = profileId,
            ble = null,
            aap = AapPodState(batteries = batteries),
            profileModel = model,
            batteryEstimateEnabled = estimateEnabled,
        )
    }

    private fun estimator(
        emissions: List<List<PodDevice>>,
        stored: Map<String, DrainProfile> = emptyMap(),
        clockMs: List<Long> = List(emissions.size) { it * 4 * 60_000L },
    ): BatteryEstimator {
        val deviceMonitor = mockk<DeviceMonitor> {
            every { devices } returns flowOf(*emissions.toTypedArray())
        }
        val drainStore = mockk<BatteryDrainStore> {
            every { profiles } returns MutableStateFlow(stored)
            coEvery { save(any(), any()) } returns Unit
        }
        val timeSource = mockk<TimeSource> {
            every { elapsedRealtime() } returnsMany clockMs
            every { now() } returns now
        }
        return BatteryEstimator(deviceMonitor, drainStore, timeSource)
    }

    /**
     * Runs the estimator over its (finite) device flow and returns the last non-empty estimate map
     * seen *during* collection. monitor() clears estimates on completion (it stops with the service),
     * so we capture the live value as it is produced rather than reading it after the flow ends.
     */
    private suspend fun TestScope.collectEstimate(estimator: BatteryEstimator): Map<String, BatteryEstimate> {
        val captured = mutableListOf<Map<String, BatteryEstimate>>()
        backgroundScope.launch { estimator.estimates.collect { captured += it } }
        estimator.monitor().collect {}
        return captured.lastOrNull { it.isNotEmpty() } ?: emptyMap()
    }

    @Test
    fun `cached-only device is not sampled`() = runTest(UnconfinedTestDispatcher()) {
        // ble == null && aap == null -> not live -> ignored.
        val offline = PodDevice(profileId = "p1", ble = null, aap = null)
        collectEstimate(estimator(listOf(listOf(offline)))) shouldBe emptyMap()
    }

    @Test
    fun `ambiguous same-profile devices are skipped`() = runTest(UnconfinedTestDispatcher()) {
        val a = device("p1", left = 0.80f, right = 0.80f)
        val b = device("p1", left = 0.50f, right = 0.50f)
        collectEstimate(estimator(listOf(listOf(a, b)))) shouldBe emptyMap()
    }

    @Test
    fun `a charging device projects runtime from the learned rate`() = runTest(UnconfinedTestDispatcher()) {
        // Docked/charging: no live drain, but we still show "what it'd last if used now" from history.
        val stored = mapOf("p1" to DrainProfile(rates = mapOf("UNKNOWN/LEFT" to learned(0.15f), "UNKNOWN/RIGHT" to learned(0.15f))))
        val result = collectEstimate(
            estimator(
                emissions = listOf(listOf(device("p1", left = 0.50f, right = 0.50f, charging = true))),
                stored = stored,
            )
        )
        val left = result["p1"].shouldNotBeNull().left.shouldNotBeNull()
        left.source shouldBe BatteryEstimate.Source.LEARNED
        // 0.50 / 0.15 * 60 == 200
        left.minutesRemaining shouldBe 200
    }

    @Test
    fun `a charging device projects runtime from the model rating`() = runTest(UnconfinedTestDispatcher()) {
        // Full AirPods Pro 2 in the case, nothing learned yet -> projects the 6h rating. 1.0 / (1/6) * 60.
        val result = collectEstimate(
            estimator(listOf(listOf(device("p1", left = 1.0f, right = 1.0f, charging = true, model = PodModel.AIRPODS_PRO2))))
        )
        val left = result["p1"].shouldNotBeNull().left.shouldNotBeNull()
        left.source shouldBe BatteryEstimate.Source.SPEC
        left.minutesRemaining shouldBe 360
    }

    @Test
    fun `a charging device with no rate shows nothing`() = runTest(UnconfinedTestDispatcher()) {
        // Unknown model, nothing learned -> no basis to project from while charging.
        collectEstimate(
            estimator(listOf(listOf(device("p1", left = 0.80f, right = 0.80f, charging = true))))
        ) shouldBe emptyMap()
    }

    @Test
    fun `a learned rate seeds an estimate immediately`() = runTest(UnconfinedTestDispatcher()) {
        // One emission, only one sample -> no live regression -> must fall back to learned rate.
        val stored = mapOf("p1" to DrainProfile(rates = mapOf("UNKNOWN/LEFT" to learned(0.15f), "UNKNOWN/RIGHT" to learned(0.15f))))
        val result = collectEstimate(
            estimator(
                emissions = listOf(listOf(device("p1", left = 0.50f, right = 0.50f))),
                stored = stored,
            )
        )

        val left = result["p1"].shouldNotBeNull().left.shouldNotBeNull()
        left.source shouldBe BatteryEstimate.Source.LEARNED
        // 0.50 / 0.15 * 60 == 200
        left.minutesRemaining shouldBe 200
    }

    @Test
    fun `a steady discharge yields a live estimate`() = runTest(UnconfinedTestDispatcher()) {
        // 5 snapshots, 1% lost every 4 minutes -> 15%/hr -> 0.15 fraction/hr.
        val emissions = (0 until 5).map { i ->
            val level = 0.80f - i * 0.01f
            listOf(device("p1", left = level, right = level))
        }
        val left = collectEstimate(estimator(emissions))["p1"].shouldNotBeNull().left.shouldNotBeNull()
        left.source shouldBe BatteryEstimate.Source.LIVE
    }

    @Test
    fun `pods draining at different rates get independent estimates`() = runTest(UnconfinedTestDispatcher()) {
        // Left drains faster (1.25%/step) than right (1%/step) over the same 4-minute steps.
        val emissions = (0 until 5).map { i ->
            listOf(device("p1", left = 0.80f - i * 0.0125f, right = 0.80f - i * 0.01f))
        }
        val estimate = collectEstimate(estimator(emissions))["p1"].shouldNotBeNull()
        val left = estimate.left.shouldNotBeNull()
        val right = estimate.right.shouldNotBeNull()
        left.source shouldBe BatteryEstimate.Source.LIVE
        right.source shouldBe BatteryEstimate.Source.LIVE
        // Faster-draining left pod must empty sooner than the right.
        (left.minutesRemaining < right.minutesRemaining) shouldBe true
    }

    @Test
    fun `a model rating seeds an estimate immediately`() = runTest(UnconfinedTestDispatcher()) {
        // One sample -> no live regression, nothing learned -> the AirPods Pro 2 rating (6h) seeds
        // the estimate at once. 1.00 / (1/6) * 60 == 360.
        val result = collectEstimate(
            estimator(listOf(listOf(device("p1", left = 1.0f, right = 1.0f, model = PodModel.AIRPODS_PRO2))))
        )
        val left = result["p1"].shouldNotBeNull().left.shouldNotBeNull()
        left.source shouldBe BatteryEstimate.Source.SPEC
        left.minutesRemaining shouldBe 360
    }

    @Test
    fun `an unknown ANC mode seeds from the shorter rating`() = runTest(UnconfinedTestDispatcher()) {
        // AirPods 4 ANC: 4h with ANC on, 5h off. The mode isn't known yet, so the shorter 4h rating
        // is used to avoid over-promising. 1.00 / (1/4) * 60 == 240.
        val result = collectEstimate(
            estimator(listOf(listOf(device("p1", left = 1.0f, right = 1.0f, model = PodModel.AIRPODS_GEN4_ANC))))
        )
        val left = result["p1"].shouldNotBeNull().left.shouldNotBeNull()
        left.source shouldBe BatteryEstimate.Source.SPEC
        left.minutesRemaining shouldBe 240
    }

    @Test
    fun `the model rating caps an over-optimistic learned rate`() = runTest(UnconfinedTestDispatcher()) {
        // A learned 0.10/hr implies 10h at full charge, beyond the Pro 2's 6h rating. The rating is a
        // hard ceiling, so the shown estimate is capped at 6h (360), not 600.
        val stored = mapOf(
            "p1" to DrainProfile(rates = mapOf("UNKNOWN/LEFT" to learned(0.10f), "UNKNOWN/RIGHT" to learned(0.10f)))
        )
        val result = collectEstimate(
            estimator(
                emissions = listOf(listOf(device("p1", left = 1.0f, right = 1.0f, model = PodModel.AIRPODS_PRO2))),
                stored = stored,
            )
        )
        val left = result["p1"].shouldNotBeNull().left.shouldNotBeNull()
        left.source shouldBe BatteryEstimate.Source.LEARNED
        left.minutesRemaining shouldBe 360
    }

    @Test
    fun `a live rate slower than the rating is capped to the rating but stays LIVE`() = runTest(UnconfinedTestDispatcher()) {
        // Measured 15%/hr on an AirPods Pro (rated 4.5h == ~22%/hr): draining slower than Apple rates,
        // so the shown life is capped to the 4.5h rating. At 0.76 that's 0.76 / (1/4.5) * 60 == 205
        // (not the ~304 the raw 15%/hr would imply). The estimate is still measured, so source == LIVE.
        val emissions = (0 until 5).map { i ->
            val level = 0.80f - i * 0.01f
            listOf(device("p1", left = level, right = level, model = PodModel.AIRPODS_PRO))
        }
        val left = collectEstimate(estimator(emissions))["p1"].shouldNotBeNull().left.shouldNotBeNull()
        left.source shouldBe BatteryEstimate.Source.LIVE
        left.minutesRemaining shouldBe 205
    }

    @Test
    fun `an implausibly fast live rate is rejected in favour of the rating`() = runTest(UnconfinedTestDispatcher()) {
        // 5%/4min == 75%/hr, far beyond 4x the Pro 2 rating (~67%/hr max plausible), so the live fit
        // is discarded and the estimate falls back to the model rating.
        val emissions = (0 until 5).map { i ->
            val level = 0.80f - i * 0.05f
            listOf(device("p1", left = level, right = level, model = PodModel.AIRPODS_PRO2))
        }
        val left = collectEstimate(estimator(emissions))["p1"].shouldNotBeNull().left.shouldNotBeNull()
        left.source shouldBe BatteryEstimate.Source.SPEC
    }

    @Test
    fun `a device with the estimate disabled is not sampled`() = runTest(UnconfinedTestDispatcher()) {
        // A clean steady discharge that WOULD yield a live estimate — but the feature is off.
        val emissions = (0 until 5).map { i ->
            val level = 0.80f - i * 0.01f
            listOf(device("p1", left = level, right = level, estimateEnabled = false))
        }
        collectEstimate(estimator(emissions)) shouldBe emptyMap()
    }

    @Test
    fun `reset deletes persisted data and drops the estimate`() = runTest(UnconfinedTestDispatcher()) {
        val drainStore = mockk<BatteryDrainStore> {
            every { profiles } returns MutableStateFlow(
                mapOf("p1" to DrainProfile(rates = mapOf("UNKNOWN/LEFT" to learned(0.15f))))
            )
            coEvery { save(any(), any()) } returns Unit
            coEvery { delete(any()) } returns Unit
        }
        val deviceMonitor = mockk<DeviceMonitor> { every { devices } returns flowOf(emptyList()) }
        val timeSource = mockk<TimeSource> {
            every { elapsedRealtime() } returns 0L
            every { now() } returns now
        }
        val estimator = BatteryEstimator(deviceMonitor, drainStore, timeSource)

        estimator.reset("p1")

        coVerify { drainStore.delete("p1") }
        estimator.estimates.value.containsKey("p1") shouldBe false
    }

    private fun learned(rate: Float) = DrainProfile.LearnedRate(
        fractionPerHour = rate,
        sampleCount = 5,
        updatedAt = now,
    )
}
