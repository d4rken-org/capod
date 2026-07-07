package eu.darken.capod.monitor.core.battery

import eu.darken.capod.common.TimeSource
import eu.darken.capod.monitor.core.DeviceMonitor
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.AapPodState
import eu.darken.capod.pods.core.apple.aap.AapPodState.Battery
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
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
        optimized: Boolean = false,
        model: PodModel? = null,
        estimateEnabled: Boolean = true,
        worn: Boolean = false,
        systemConnected: Boolean = false,
        ancMode: AapSetting.AncMode.Value? = null,
        ancSupported: List<AapSetting.AncMode.Value> = AapSetting.AncMode.Value.entries,
    ): PodDevice {
        val state = when {
            optimized -> ChargingState.CHARGING_OPTIMIZED
            charging -> ChargingState.CHARGING
            else -> ChargingState.NOT_CHARGING
        }
        val batteries = buildMap {
            if (left != null) put(BatteryType.LEFT, Battery(BatteryType.LEFT, left, state))
            if (right != null) put(BatteryType.RIGHT, Battery(BatteryType.RIGHT, right, state))
        }
        val settings = buildMap<kotlin.reflect.KClass<out AapSetting>, AapSetting> {
            if (worn) put(
                AapSetting.EarDetection::class,
                AapSetting.EarDetection(
                    primaryPod = AapSetting.EarDetection.PodPlacement.IN_EAR,
                    secondaryPod = AapSetting.EarDetection.PodPlacement.IN_EAR,
                ),
            )
            if (ancMode != null) put(
                AapSetting.AncMode::class,
                AapSetting.AncMode(current = ancMode, supported = ancSupported),
            )
        }
        return PodDevice(
            profileId = profileId,
            ble = null,
            aap = AapPodState(batteries = batteries, settings = settings),
            profileModel = model,
            batteryEstimateEnabled = estimateEnabled,
            isSystemConnected = systemConnected,
        )
    }

    private fun estimator(
        emissions: List<List<PodDevice>>,
        stored: Map<String, DrainProfile> = emptyMap(),
        clockMs: List<Long> = List(emissions.size) { it * 4 * 60_000L },
        musicActive: List<Boolean> = List(emissions.size) { false },
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
        val audioManager = mockk<android.media.AudioManager> {
            every { isMusicActive } returnsMany musicActive
        }
        return BatteryEstimator(deviceMonitor, drainStore, timeSource, audioManager)
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
    fun `a rising charge yields a live time-until-charged`() = runTest(UnconfinedTestDispatcher()) {
        // 2%/min while docked -> 1.2 fraction/hr -> at 44% that's (1 - 0.44) / 1.2 * 60 == 28 min.
        val emissions = (0 until 4).map { i ->
            val level = 0.20f + i * 0.08f
            listOf(device("p1", left = level, right = level, charging = true, model = PodModel.AIRPODS_PRO2))
        }
        val left = collectEstimate(estimator(emissions))["p1"].shouldNotBeNull().left.shouldNotBeNull()
        left.minutesUntilCharged shouldBe 28
    }

    @Test
    fun `the quick-charge rating seeds an ETA on the very first charge`() = runTest(UnconfinedTestDispatcher()) {
        // Nothing measured, nothing stored — Apple's "5 minutes = ~1 hour of listening" claim
        // (2.0/hr for a Pro 2) seeds the bands with the taper haircut: 30% of bulk at 2.0/hr (9m)
        // + taper at 1.0/hr (6m) + trickle at 0.6/hr (10m) == 25 min.
        val result = collectEstimate(
            estimator(listOf(listOf(device("p1", left = 0.50f, right = 0.50f, charging = true, model = PodModel.AIRPODS_PRO2))))
        )
        result["p1"].shouldNotBeNull().left.shouldNotBeNull().minutesUntilCharged shouldBe 25
    }

    @Test
    fun `learned band rates shape the ETA through the taper`() = runTest(UnconfinedTestDispatcher()) {
        // At 85% the linear scalar (1.2/hr) would claim 8m; the learned bands know the taper is
        // slower: 5% of taper at 1.0/hr (3m) + trickle at 0.6/hr (10m) == 13m.
        val bands = mapOf(
            "BULK" to learned(2.0f),
            "TAPER" to learned(1.0f),
            "TRICKLE" to learned(0.6f),
        )
        val stored = mapOf(
            "p1" to DrainProfile(
                chargeRates = mapOf("LEFT" to learned(1.2f), "RIGHT" to learned(1.2f)),
                chargeBands = mapOf("LEFT" to bands, "RIGHT" to bands),
            )
        )
        val result = collectEstimate(
            estimator(
                emissions = listOf(listOf(device("p1", left = 0.85f, right = 0.85f, charging = true, model = PodModel.AIRPODS_PRO2))),
                stored = stored,
            )
        )
        result["p1"].shouldNotBeNull().left.shouldNotBeNull().minutesUntilCharged shouldBe 13
    }

    @Test
    fun `a stored charge rate seeds time-until-charged immediately`() = runTest(UnconfinedTestDispatcher()) {
        // First charging emission, no live fit possible yet -> the persisted rate answers at once.
        // 50% missing at 1.2/hr == 25 min.
        val stored = mapOf(
            "p1" to DrainProfile(chargeRates = mapOf("LEFT" to learned(1.2f), "RIGHT" to learned(1.2f)))
        )
        val result = collectEstimate(
            estimator(
                emissions = listOf(listOf(device("p1", left = 0.50f, right = 0.50f, charging = true, model = PodModel.AIRPODS_PRO2))),
                stored = stored,
            )
        )
        result["p1"].shouldNotBeNull().left.shouldNotBeNull().minutesUntilCharged shouldBe 25
    }

    @Test
    fun `an optimized-charging hold suppresses time-until-charged`() = runTest(UnconfinedTestDispatcher()) {
        // CHARGING_OPTIMIZED parks the level below full — an ETA would mislead, but the runtime
        // projection stays visible.
        val stored = mapOf(
            "p1" to DrainProfile(chargeRates = mapOf("LEFT" to learned(1.2f), "RIGHT" to learned(1.2f)))
        )
        val result = collectEstimate(
            estimator(
                emissions = listOf(
                    listOf(device("p1", left = 0.80f, right = 0.80f, charging = true, optimized = true, model = PodModel.AIRPODS_PRO2))
                ),
                stored = stored,
            )
        )
        val left = result["p1"].shouldNotBeNull().left.shouldNotBeNull()
        left.minutesUntilCharged shouldBe null
        left.source shouldBe BatteryEstimate.Source.SPEC
    }

    @Test
    fun `a stalled charge suppresses time-until-charged`() = runTest(UnconfinedTestDispatcher()) {
        // Level stops rising while still flagged charging (unreported hold / trickle): once the
        // silence outlasts the stall threshold the frozen ETA is dropped.
        val stored = mapOf(
            "p1" to DrainProfile(chargeRates = mapOf("LEFT" to learned(1.2f), "RIGHT" to learned(1.2f)))
        )
        val emissions = listOf(
            listOf(device("p1", left = 0.50f, right = 0.50f, charging = true, model = PodModel.AIRPODS_PRO2)),
            listOf(device("p1", left = 0.50f, right = 0.50f, charging = true, model = PodModel.AIRPODS_PRO2)),
        )
        val result = collectEstimate(
            estimator(emissions, stored = stored, clockMs = listOf(0L, 11 * 60_000L)),
        )
        result["p1"].shouldNotBeNull().left.shouldNotBeNull().minutesUntilCharged shouldBe null
    }

    @Test
    fun `a discharging pod has no charge estimate`() = runTest(UnconfinedTestDispatcher()) {
        val stored = mapOf(
            "p1" to DrainProfile(chargeRates = mapOf("LEFT" to learned(1.2f), "RIGHT" to learned(1.2f)))
        )
        val emissions = (0 until 5).map { i ->
            val level = 0.80f - i * 0.01f
            listOf(device("p1", left = level, right = level))
        }
        val left = collectEstimate(estimator(emissions, stored = stored))["p1"].shouldNotBeNull().left.shouldNotBeNull()
        left.source shouldBe BatteryEstimate.Source.LIVE
        left.minutesUntilCharged shouldBe null
    }

    @Test
    fun `charge rates and band rates are persisted`() = runTest(UnconfinedTestDispatcher()) {
        val drainStore = mockk<BatteryDrainStore> {
            every { profiles } returns MutableStateFlow(emptyMap())
            coEvery { save(any(), any()) } returns Unit
        }
        val emissions = (0 until 4).map { i ->
            val level = 0.20f + i * 0.08f
            listOf(device("p1", left = level, right = level, charging = true, model = PodModel.AIRPODS_PRO2))
        }
        val deviceMonitor = mockk<DeviceMonitor> { every { devices } returns flowOf(*emissions.toTypedArray()) }
        val timeSource = mockk<TimeSource> {
            every { elapsedRealtime() } returnsMany emissions.indices.map { it * 4 * 60_000L }
            every { now() } returns now
        }
        val audioManager = mockk<android.media.AudioManager> { every { isMusicActive } returns false }
        val estimator = BatteryEstimator(deviceMonitor, drainStore, timeSource, audioManager)

        estimator.monitor().collect {}

        coVerify {
            drainStore.save("p1", match {
                it.chargeRates.containsKey("LEFT") && it.chargeRates.containsKey("RIGHT") &&
                    it.chargeBands["LEFT"]?.containsKey("BULK") == true
            })
        }
    }

    @Test
    fun `worn playing segments feed the listening rates`() = runTest(UnconfinedTestDispatcher()) {
        // Steady discharge while worn, playing, and system-connected: learned into BOTH the
        // general rates and the health-grade listening rates.
        val emissions = (0 until 5).map { i ->
            val level = 0.80f - i * 0.01f
            listOf(device("p1", left = level, right = level, worn = true, systemConnected = true))
        }
        val drainStore = mockk<BatteryDrainStore> {
            every { profiles } returns MutableStateFlow(emptyMap())
            coEvery { save(any(), any()) } returns Unit
        }
        val deviceMonitor = mockk<DeviceMonitor> { every { devices } returns flowOf(*emissions.toTypedArray()) }
        val timeSource = mockk<TimeSource> {
            every { elapsedRealtime() } returnsMany emissions.indices.map { it * 4 * 60_000L }
            every { now() } returns now
        }
        val audioManager = mockk<android.media.AudioManager> { every { isMusicActive } returns true }
        BatteryEstimator(deviceMonitor, drainStore, timeSource, audioManager).monitor().collect {}

        coVerify {
            drainStore.save("p1", match {
                it.listeningRates.containsKey("UNKNOWN/LEFT") && it.rates.containsKey("UNKNOWN/LEFT")
            })
        }
    }

    @Test
    fun `idle wear does not feed the listening rates`() = runTest(UnconfinedTestDispatcher()) {
        // Worn and connected but nothing playing: general rates learn, listening rates stay empty.
        val emissions = (0 until 5).map { i ->
            val level = 0.80f - i * 0.01f
            listOf(device("p1", left = level, right = level, worn = true, systemConnected = true))
        }
        val drainStore = mockk<BatteryDrainStore> {
            every { profiles } returns MutableStateFlow(emptyMap())
            coEvery { save(any(), any()) } returns Unit
        }
        val deviceMonitor = mockk<DeviceMonitor> { every { devices } returns flowOf(*emissions.toTypedArray()) }
        val timeSource = mockk<TimeSource> {
            every { elapsedRealtime() } returnsMany emissions.indices.map { it * 4 * 60_000L }
            every { now() } returns now
        }
        val audioManager = mockk<android.media.AudioManager> { every { isMusicActive } returns false }
        BatteryEstimator(deviceMonitor, drainStore, timeSource, audioManager).monitor().collect {}

        coVerify {
            drainStore.save("p1", match { it.rates.containsKey("UNKNOWN/LEFT") && it.listeningRates.isEmpty() })
        }
    }

    @Test
    fun `playback on another sink does not feed the listening rates`() = runTest(UnconfinedTestDispatcher()) {
        // Music is playing but this device is NOT the system's audio sink (phone speaker, car):
        // treating it as pod listening would poison health.
        val emissions = (0 until 5).map { i ->
            val level = 0.80f - i * 0.01f
            listOf(device("p1", left = level, right = level, worn = true, systemConnected = false))
        }
        val drainStore = mockk<BatteryDrainStore> {
            every { profiles } returns MutableStateFlow(emptyMap())
            coEvery { save(any(), any()) } returns Unit
        }
        val deviceMonitor = mockk<DeviceMonitor> { every { devices } returns flowOf(*emissions.toTypedArray()) }
        val timeSource = mockk<TimeSource> {
            every { elapsedRealtime() } returnsMany emissions.indices.map { it * 4 * 60_000L }
            every { now() } returns now
        }
        val audioManager = mockk<android.media.AudioManager> { every { isMusicActive } returns true }
        BatteryEstimator(deviceMonitor, drainStore, timeSource, audioManager).monitor().collect {}

        coVerify {
            drainStore.save("p1", match { it.rates.containsKey("UNKNOWN/LEFT") && it.listeningRates.isEmpty() })
        }
    }

    @Test
    fun `a listening segment is flushed when playback stops`() = runTest(UnconfinedTestDispatcher()) {
        // 1-minute cadence: fit persists at the 4th sample (t=3), further drops are inside the
        // persistence cooldown — then playback stops. The closed segment must be flushed and
        // persisted anyway, not silently discarded with the cleared window.
        val worn = (0 until 5).map { i ->
            listOf(device("p1", left = 0.80f - i * 0.01f, right = 0.80f - i * 0.01f, worn = true, systemConnected = true))
        }
        val after = listOf(listOf(device("p1", left = 0.75f, right = 0.75f, worn = true, systemConnected = true)))
        val emissions = worn + after
        val drainStore = mockk<BatteryDrainStore> {
            every { profiles } returns MutableStateFlow(emptyMap())
            coEvery { save(any(), any()) } returns Unit
        }
        val deviceMonitor = mockk<DeviceMonitor> { every { devices } returns flowOf(*emissions.toTypedArray()) }
        val timeSource = mockk<TimeSource> {
            every { elapsedRealtime() } returnsMany emissions.indices.map { it * 60_000L }
            every { now() } returns now
        }
        val audioManager = mockk<android.media.AudioManager> {
            every { isMusicActive } returnsMany listOf(true, true, true, true, true, false)
        }
        BatteryEstimator(deviceMonitor, drainStore, timeSource, audioManager).monitor().collect {}

        // Two listening persists: the cadence one mid-segment, and the forced flush at gate-off.
        coVerify(atLeast = 2) {
            drainStore.save("p1", match { it.listeningRates.containsKey("UNKNOWN/LEFT") })
        }
    }

    @Test
    fun `undocking does not leak charge samples into the drain fit`() = runTest(UnconfinedTestDispatcher()) {
        // A charge session builds a rising window; the moment the pods leave the case the window
        // must flip to drain from scratch — a fit across the rising samples would be garbage.
        val emissions = listOf(
            listOf(device("p1", left = 0.20f, right = 0.20f, charging = true, model = PodModel.AIRPODS_PRO2)),
            listOf(device("p1", left = 0.28f, right = 0.28f, charging = true, model = PodModel.AIRPODS_PRO2)),
            listOf(device("p1", left = 0.36f, right = 0.36f, charging = true, model = PodModel.AIRPODS_PRO2)),
            listOf(device("p1", left = 0.36f, right = 0.36f, model = PodModel.AIRPODS_PRO2)), // undocked
        )
        val left = collectEstimate(estimator(emissions))["p1"].shouldNotBeNull().left.shouldNotBeNull()
        // One drain sample only -> no live fit, nothing learned -> the rating answers.
        left.source shouldBe BatteryEstimate.Source.SPEC
        left.minutesUntilCharged shouldBe null
    }

    @Test
    fun `learned rates from different hardware are ignored`() = runTest(UnconfinedTestDispatcher()) {
        // The profile was re-pointed from an AirPods Pro to a Pro 2 — its old rates don't describe
        // this device, so the estimate falls back to the current model's rating.
        val stored = mapOf(
            "p1" to DrainProfile(
                model = PodModel.AIRPODS_PRO.name,
                rates = mapOf("UNKNOWN/LEFT" to learned(0.15f), "UNKNOWN/RIGHT" to learned(0.15f)),
            )
        )
        val result = collectEstimate(
            estimator(
                emissions = listOf(listOf(device("p1", left = 1.0f, right = 1.0f, model = PodModel.AIRPODS_PRO2))),
                stored = stored,
            )
        )
        result["p1"].shouldNotBeNull().left.shouldNotBeNull().source shouldBe BatteryEstimate.Source.SPEC
    }

    @Test
    fun `an empty ANC bucket borrows the sibling rate instead of jumping to spec`() = runTest(UnconfinedTestDispatcher()) {
        // Pro 2: OFF learned (5h-equivalent), user toggles to ON whose bucket is empty. Both modes
        // rate at 6h so the scale is 1 — ON reuses OFF's measured 0.20/hr (300 min) rather than the
        // optimistic 6h spec (360). This is the +1h "ANC increases battery" paradox, removed.
        val stored = mapOf("p1" to DrainProfile(rates = mapOf("OFF/LEFT" to learned(0.20f), "OFF/RIGHT" to learned(0.20f))))
        val result = collectEstimate(
            estimator(
                emissions = listOf(listOf(device("p1", left = 1.0f, right = 1.0f, model = PodModel.AIRPODS_PRO2, ancMode = AapSetting.AncMode.Value.ON))),
                stored = stored,
            )
        )
        val left = result["p1"].shouldNotBeNull().left.shouldNotBeNull()
        left.source shouldBe BatteryEstimate.Source.LEARNED
        left.minutesRemaining shouldBe 300
    }

    @Test
    fun `an empty bucket prefers the more conservative of UNKNOWN and the sibling`() = runTest(UnconfinedTestDispatcher()) {
        // A mode-agnostic UNKNOWN reading (0.12/hr, optimistic) AND a real OFF sibling (0.20/hr) both
        // exist while ON is empty. The estimate takes the less-optimistic of the two so a toggle can't
        // inflate past the sibling: 0.20/hr -> 300, not the 360 the optimistic UNKNOWN would clamp to.
        val stored = mapOf(
            "p1" to DrainProfile(
                rates = mapOf(
                    "UNKNOWN/LEFT" to learned(0.12f), "UNKNOWN/RIGHT" to learned(0.12f),
                    "OFF/LEFT" to learned(0.20f), "OFF/RIGHT" to learned(0.20f),
                )
            )
        )
        val result = collectEstimate(
            estimator(
                emissions = listOf(listOf(device("p1", left = 1.0f, right = 1.0f, model = PodModel.AIRPODS_PRO2, ancMode = AapSetting.AncMode.Value.ON))),
                stored = stored,
            )
        )
        val left = result["p1"].shouldNotBeNull().left.shouldNotBeNull()
        left.source shouldBe BatteryEstimate.Source.LEARNED
        left.minutesRemaining shouldBe 300
    }

    @Test
    fun `a borrowed sibling rate is scaled by the modes' rated drain`() = runTest(UnconfinedTestDispatcher()) {
        // AirPods Pro (gen1) rates ANC on at 4.5h, off at 5h. An empty ON bucket borrows the OFF
        // learned 0.20/hr and scales it by (1/4.5)/(1/5) == 1.111 -> 0.222/hr -> 270 min (4.5h). ANC
        // on shows LESS than OFF's 300 min, the physically correct direction.
        val stored = mapOf("p1" to DrainProfile(rates = mapOf("OFF/LEFT" to learned(0.20f), "OFF/RIGHT" to learned(0.20f))))
        val result = collectEstimate(
            estimator(
                emissions = listOf(listOf(device("p1", left = 1.0f, right = 1.0f, model = PodModel.AIRPODS_PRO, ancMode = AapSetting.AncMode.Value.ON))),
                stored = stored,
            )
        )
        val left = result["p1"].shouldNotBeNull().left.shouldNotBeNull()
        left.source shouldBe BatteryEstimate.Source.LEARNED
        left.minutesRemaining shouldBe 270
    }

    @Test
    fun `sibling scaling works in the inverse direction too`() = runTest(UnconfinedTestDispatcher()) {
        // gen1 Pro: only ON learned (0.30/hr). An empty OFF bucket borrows it scaled by
        // (1/5)/(1/4.5) == 0.9 -> 0.27/hr -> 222 min. OFF drains slower than the measured ON, correct.
        val stored = mapOf("p1" to DrainProfile(rates = mapOf("ON/LEFT" to learned(0.30f), "ON/RIGHT" to learned(0.30f))))
        val result = collectEstimate(
            estimator(
                emissions = listOf(listOf(device("p1", left = 1.0f, right = 1.0f, model = PodModel.AIRPODS_PRO, ancMode = AapSetting.AncMode.Value.OFF))),
                stored = stored,
            )
        )
        val left = result["p1"].shouldNotBeNull().left.shouldNotBeNull()
        left.source shouldBe BatteryEstimate.Source.LEARNED
        left.minutesRemaining shouldBe 222
    }

    @Test
    fun `an empty bucket with no sibling still falls back to spec`() = runTest(UnconfinedTestDispatcher()) {
        // Nothing learned in any mode -> the fallback can't fire, the model rating seeds as before.
        val result = collectEstimate(
            estimator(listOf(listOf(device("p1", left = 1.0f, right = 1.0f, model = PodModel.AIRPODS_PRO2, ancMode = AapSetting.AncMode.Value.ON))))
        )
        val left = result["p1"].shouldNotBeNull().left.shouldNotBeNull()
        left.source shouldBe BatteryEstimate.Source.SPEC
        left.minutesRemaining shouldBe 360
    }

    @Test
    fun `a populated current bucket is never overridden by a sibling`() = runTest(UnconfinedTestDispatcher()) {
        // Real ON data (0.30/hr) exists alongside OFF (0.20/hr). The current mode's own measurement
        // wins outright -> 200 min; a genuine per-mode difference is preserved, not flattened.
        val stored = mapOf(
            "p1" to DrainProfile(
                rates = mapOf(
                    "ON/LEFT" to learned(0.30f), "ON/RIGHT" to learned(0.30f),
                    "OFF/LEFT" to learned(0.20f), "OFF/RIGHT" to learned(0.20f),
                )
            )
        )
        val result = collectEstimate(
            estimator(
                emissions = listOf(listOf(device("p1", left = 1.0f, right = 1.0f, model = PodModel.AIRPODS_PRO2, ancMode = AapSetting.AncMode.Value.ON))),
                stored = stored,
            )
        )
        val left = result["p1"].shouldNotBeNull().left.shouldNotBeNull()
        left.source shouldBe BatteryEstimate.Source.LEARNED
        left.minutesRemaining shouldBe 200
    }

    @Test
    fun `the best-evidenced sibling is chosen`() = runTest(UnconfinedTestDispatcher()) {
        // ON empty; OFF (0.20/hr, 1 update) and TRANSPARENCY (0.40/hr, 5 updates) both available and
        // same-rated (all non-off modes rate 6h on a Pro 2, so scale 1). The higher-evidence
        // TRANSPARENCY rate wins -> 150 min, not the 300 the thinner OFF rate would give.
        val stored = mapOf(
            "p1" to DrainProfile(
                rates = mapOf(
                    "OFF/LEFT" to learned(0.20f, updateCount = 1), "OFF/RIGHT" to learned(0.20f, updateCount = 1),
                    "TRANSPARENCY/LEFT" to learned(0.40f, updateCount = 5), "TRANSPARENCY/RIGHT" to learned(0.40f, updateCount = 5),
                )
            )
        )
        val result = collectEstimate(
            estimator(
                emissions = listOf(listOf(device("p1", left = 1.0f, right = 1.0f, model = PodModel.AIRPODS_PRO2, ancMode = AapSetting.AncMode.Value.ON))),
                stored = stored,
            )
        )
        result["p1"].shouldNotBeNull().left.shouldNotBeNull().minutesRemaining shouldBe 150
    }

    @Test
    fun `equal-evidence siblings tie-break on closest rated drain`() = runTest(UnconfinedTestDispatcher()) {
        // gen1 Pro rates ON and TRANSPARENCY at 4.5h but OFF at 5h. With equal evidence, the sibling
        // whose rating is closest to ON (TRANSPARENCY, identical rating) is the better predictor and
        // wins over OFF: 0.40/hr -> 150. Had OFF (0.20/hr) won, scaling would give 0.222/hr -> 270.
        val stored = mapOf(
            "p1" to DrainProfile(
                rates = mapOf(
                    "OFF/LEFT" to learned(0.20f, updateCount = 3), "OFF/RIGHT" to learned(0.20f, updateCount = 3),
                    "TRANSPARENCY/LEFT" to learned(0.40f, updateCount = 3), "TRANSPARENCY/RIGHT" to learned(0.40f, updateCount = 3),
                )
            )
        )
        val result = collectEstimate(
            estimator(
                emissions = listOf(listOf(device("p1", left = 1.0f, right = 1.0f, model = PodModel.AIRPODS_PRO, ancMode = AapSetting.AncMode.Value.ON))),
                stored = stored,
            )
        )
        result["p1"].shouldNotBeNull().left.shouldNotBeNull().minutesRemaining shouldBe 150
    }

    @Test
    fun `equal-evidence equal-rated siblings tie-break on recency`() = runTest(UnconfinedTestDispatcher()) {
        // TRANSPARENCY and ADAPTIVE both rate identically to ON (4.5h) with equal evidence — only
        // recency separates them. The newer ADAPTIVE (0.50/hr) wins over the older TRANSPARENCY
        // (0.40/hr): 0.50/hr -> 120, not 150.
        val stored = mapOf(
            "p1" to DrainProfile(
                rates = mapOf(
                    "TRANSPARENCY/LEFT" to learned(0.40f, updateCount = 2, updatedAt = now.minusSeconds(3600)),
                    "TRANSPARENCY/RIGHT" to learned(0.40f, updateCount = 2, updatedAt = now.minusSeconds(3600)),
                    "ADAPTIVE/LEFT" to learned(0.50f, updateCount = 2, updatedAt = now),
                    "ADAPTIVE/RIGHT" to learned(0.50f, updateCount = 2, updatedAt = now),
                )
            )
        )
        val result = collectEstimate(
            estimator(
                emissions = listOf(listOf(device("p1", left = 1.0f, right = 1.0f, model = PodModel.AIRPODS_PRO, ancMode = AapSetting.AncMode.Value.ON))),
                stored = stored,
            )
        )
        result["p1"].shouldNotBeNull().left.shouldNotBeNull().minutesRemaining shouldBe 120
    }

    @Test
    fun `the UNKNOWN bucket does not borrow sibling rates`() = runTest(UnconfinedTestDispatcher()) {
        // BLE-only (mode not known) keeps its conservative spec-min behaviour: a real OFF sibling is
        // NOT borrowed, the estimate stays on the 6h rating (360), not OFF's 300.
        val stored = mapOf("p1" to DrainProfile(rates = mapOf("OFF/LEFT" to learned(0.20f), "OFF/RIGHT" to learned(0.20f))))
        val result = collectEstimate(
            estimator(
                emissions = listOf(listOf(device("p1", left = 1.0f, right = 1.0f, model = PodModel.AIRPODS_PRO2))),
                stored = stored,
            )
        )
        val left = result["p1"].shouldNotBeNull().left.shouldNotBeNull()
        left.source shouldBe BatteryEstimate.Source.SPEC
        left.minutesRemaining shouldBe 360
    }

    @Test
    fun `a model without ratings does not borrow a sibling rate`() = runTest(UnconfinedTestDispatcher()) {
        // Beats Fit Pro has ANC but no published battery rating -> no spec ceiling to clamp a borrowed
        // rate, so the fallback is skipped entirely and nothing over-promises (no estimate at all).
        val stored = mapOf("p1" to DrainProfile(rates = mapOf("OFF/LEFT" to learned(0.20f), "OFF/RIGHT" to learned(0.20f))))
        collectEstimate(
            estimator(
                emissions = listOf(listOf(device("p1", left = 1.0f, right = 1.0f, model = PodModel.BEATS_FIT_PRO, ancMode = AapSetting.AncMode.Value.ON))),
                stored = stored,
            )
        ) shouldBe emptyMap()
    }

    @Test
    fun `an unsupported sibling mode is not borrowed`() = runTest(UnconfinedTestDispatcher()) {
        // A stale ADAPTIVE key exists, but the device only reports OFF/ON as supported -> the stale
        // key is ignored, no other sibling has data, so the estimate stays on spec (360).
        val stored = mapOf("p1" to DrainProfile(rates = mapOf("ADAPTIVE/LEFT" to learned(0.20f), "ADAPTIVE/RIGHT" to learned(0.20f))))
        val result = collectEstimate(
            estimator(
                emissions = listOf(
                    listOf(
                        device(
                            "p1", left = 1.0f, right = 1.0f, model = PodModel.AIRPODS_PRO2,
                            ancMode = AapSetting.AncMode.Value.ON,
                            ancSupported = listOf(AapSetting.AncMode.Value.OFF, AapSetting.AncMode.Value.ON),
                        )
                    )
                ),
                stored = stored,
            )
        )
        val left = result["p1"].shouldNotBeNull().left.shouldNotBeNull()
        left.source shouldBe BatteryEstimate.Source.SPEC
        left.minutesRemaining shouldBe 360
    }

    @Test
    fun `the in-case runtime projection also borrows a sibling rate`() = runTest(UnconfinedTestDispatcher()) {
        // Charging (no live drain) in an empty ON bucket: the "if used now" projection borrows the OFF
        // sibling (0.20/hr) instead of spec. At 50% that's 0.50 / 0.20 * 60 == 150.
        val stored = mapOf("p1" to DrainProfile(rates = mapOf("OFF/LEFT" to learned(0.20f), "OFF/RIGHT" to learned(0.20f))))
        val result = collectEstimate(
            estimator(
                emissions = listOf(listOf(device("p1", left = 0.50f, right = 0.50f, charging = true, model = PodModel.AIRPODS_PRO2, ancMode = AapSetting.AncMode.Value.ON))),
                stored = stored,
            )
        )
        val left = result["p1"].shouldNotBeNull().left.shouldNotBeNull()
        left.source shouldBe BatteryEstimate.Source.LEARNED
        left.minutesRemaining shouldBe 150
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
        val audioManager = mockk<android.media.AudioManager> { every { isMusicActive } returns false }
        val estimator = BatteryEstimator(deviceMonitor, drainStore, timeSource, audioManager)

        estimator.reset("p1")

        coVerify { drainStore.delete("p1") }
        estimator.estimates.value.containsKey("p1") shouldBe false
    }

    private fun learned(rate: Float, updateCount: Int = 1, updatedAt: Instant = now) = DrainProfile.LearnedRate(
        fractionPerHour = rate,
        sampleCount = 5,
        updateCount = updateCount,
        updatedAt = updatedAt,
    )
}
