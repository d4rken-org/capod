package eu.darken.capod.monitor.core.battery

import eu.darken.capod.common.TimeSource
import eu.darken.capod.monitor.core.DeviceMonitor
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.aap.AapPodState
import eu.darken.capod.pods.core.apple.aap.AapPodState.Battery
import eu.darken.capod.pods.core.apple.aap.AapPodState.BatteryType
import eu.darken.capod.pods.core.apple.aap.AapPodState.ChargingState
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
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
    ): PodDevice {
        val state = if (charging) ChargingState.CHARGING else ChargingState.NOT_CHARGING
        val batteries = buildMap {
            if (left != null) put(BatteryType.LEFT, Battery(BatteryType.LEFT, left, state))
            if (right != null) put(BatteryType.RIGHT, Battery(BatteryType.RIGHT, right, state))
        }
        return PodDevice(profileId = profileId, ble = null, aap = AapPodState(batteries = batteries))
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
    fun `charging device produces no estimate even with learned rate`() = runTest(UnconfinedTestDispatcher()) {
        val stored = mapOf("p1" to DrainProfile(rates = mapOf("UNKNOWN/LEFT" to learned(0.15f), "UNKNOWN/RIGHT" to learned(0.15f))))
        val result = collectEstimate(
            estimator(
                emissions = listOf(listOf(device("p1", left = 0.50f, right = 0.50f, charging = true))),
                stored = stored,
            )
        )
        result shouldBe emptyMap()
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
        left.isLearned shouldBe true
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
        left.isLearned shouldBe false
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
        left.isLearned shouldBe false
        right.isLearned shouldBe false
        // Faster-draining left pod must empty sooner than the right.
        (left.minutesRemaining < right.minutesRemaining) shouldBe true
    }

    private fun learned(rate: Float) = DrainProfile.LearnedRate(
        fractionPerHour = rate,
        sampleCount = 5,
        updatedAt = now,
    )
}
