package eu.darken.capod.monitor.core.battery

import eu.darken.capod.monitor.core.PodDevice
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class BatteryEstimateExtensionsTest : BaseTest() {

    private val estimate = BatteryEstimate(
        left = BatteryEstimate.Pod(minutesRemaining = 120, fractionPerHour = 0.2f, source = BatteryEstimate.Source.LIVE),
    )

    private fun device(
        profileId: String? = "profile-1",
        live: Boolean = true,
        enabled: Boolean = true,
    ): PodDevice = mockk {
        every { this@mockk.profileId } returns profileId
        every { this@mockk.isLive } returns live
        every { this@mockk.batteryEstimateEnabled } returns enabled
    }

    @Test
    fun `estimateFor returns the profile's estimate for an enabled live device`() {
        mapOf("profile-1" to estimate).estimateFor(device()) shouldBe estimate
    }

    @Test
    fun `estimateFor is null when the per-device toggle is off`() {
        mapOf("profile-1" to estimate).estimateFor(device(enabled = false)).shouldBeNull()
    }

    @Test
    fun `estimateFor is null for cached-only devices`() {
        mapOf("profile-1" to estimate).estimateFor(device(live = false)).shouldBeNull()
    }

    @Test
    fun `estimateFor is null for anonymous devices`() {
        mapOf("profile-1" to estimate).estimateFor(device(profileId = null)).shouldBeNull()
    }

    @Test
    fun `estimateFor is null when no estimate exists for the profile`() {
        mapOf("other-profile" to estimate).estimateFor(device()).shouldBeNull()
        emptyMap<String, BatteryEstimate>().estimateFor(device()).shouldBeNull()
    }

    @Test
    fun `takeFor applies the same gating to an already-resolved estimate`() {
        estimate.takeFor(device()) shouldBe estimate
        estimate.takeFor(device(enabled = false)).shouldBeNull()
        estimate.takeFor(device(live = false)).shouldBeNull()
        estimate.takeFor(device(profileId = null)).shouldBeNull()
    }

    @Test
    fun `displayMinutes shows the runtime projection while not charging`() {
        val pod = BatteryEstimate.Pod(
            minutesRemaining = 300,
            fractionPerHour = 0.2f,
            source = BatteryEstimate.Source.LIVE,
            minutesUntilCharged = 25,
        )
        pod.displayMinutes(charging = false) shouldBe 300
    }

    @Test
    fun `displayMinutes swaps to the charge ETA while charging`() {
        val pod = BatteryEstimate.Pod(
            minutesRemaining = 300,
            fractionPerHour = 0.2f,
            source = BatteryEstimate.Source.LIVE,
            minutesUntilCharged = 25,
        )
        pod.displayMinutes(charging = true) shouldBe 25
    }

    @Test
    fun `displayMinutes shows nothing while charging without a usable ETA`() {
        val pod = BatteryEstimate.Pod(
            minutesRemaining = 300,
            fractionPerHour = 0.2f,
            source = BatteryEstimate.Source.LIVE,
            minutesUntilCharged = null,
        )
        pod.displayMinutes(charging = true).shouldBeNull()
    }

    private fun chargingDevice(
        leftCharging: Boolean = false,
        rightCharging: Boolean = false,
        headsetCharging: Boolean = false,
    ): PodDevice = mockk {
        every { isLeftPodCharging } returns leftCharging
        every { isRightPodCharging } returns rightCharging
        every { isHeadsetBeingCharged } returns headsetCharging
    }

    private fun pod(
        minutes: Int = 300,
        rate: Float = 0.2f,
        source: BatteryEstimate.Source = BatteryEstimate.Source.LIVE,
        untilCharged: Int? = null,
    ) = BatteryEstimate.Pod(
        minutesRemaining = minutes,
        fractionPerHour = rate,
        source = source,
        minutesUntilCharged = untilCharged,
    )

    @Test
    fun `invisible estimate churn keeps the display key stable`() {
        // Rate and source change constantly while the displayed minutes stay put — render
        // triggers deduped on the display key must not fire for those.
        val before = BatteryEstimate(left = pod(rate = 0.20f, source = BatteryEstimate.Source.LEARNED))
        val after = BatteryEstimate(left = pod(rate = 0.21f, source = BatteryEstimate.Source.LIVE))

        before.displayKey(chargingDevice()) shouldBe after.displayKey(chargingDevice())
    }

    @Test
    fun `displayed minutes changes alter the display key`() {
        val before = BatteryEstimate(left = pod(minutes = 300))
        val after = BatteryEstimate(left = pod(minutes = 299))

        before.displayKey(chargingDevice()) shouldNotBe after.displayKey(chargingDevice())
    }

    @Test
    fun `display key selects the charge ETA per charging slot`() {
        val full = BatteryEstimate(
            left = pod(minutes = 300, untilCharged = 25),
            right = pod(minutes = 280, untilCharged = null),
            headset = null,
        )

        full.displayKey(chargingDevice(leftCharging = true, rightCharging = true)) shouldBe listOf(25, null, null)
        full.displayKey(chargingDevice()) shouldBe listOf(300, 280, null)
    }

    @Test
    fun `display key is null when nothing would be rendered`() {
        // A charging pod without a usable ETA renders exactly like no estimate at all.
        val suppressed = BatteryEstimate(left = pod(minutes = 300, untilCharged = null))

        suppressed.displayKey(chargingDevice(leftCharging = true)).shouldBeNull()
    }
}
