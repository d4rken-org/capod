package eu.darken.capod.monitor.core.cache

import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.ble.devices.DualApplePods
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant

class ToCachedStateTest : BaseTest() {

    private val now = Instant.parse("2026-04-02T12:00:00Z")

    private fun mockDualPod(
        leftBattery: Float? = null,
        rightBattery: Float? = null,
        caseBattery: Float? = null,
    ): DualApplePods = mockk(relaxed = true) {
        every { batteryLeftPodPercent } returns leftBattery
        every { batteryRightPodPercent } returns rightBattery
        every { batteryCasePercent } returns caseBattery
        every { model } returns PodModel.AIRPODS_PRO3
        every { seenLastAt } returns now
    }

    private fun liveDevice(
        leftBattery: Float? = 0.8f,
        rightBattery: Float? = 0.7f,
        caseBattery: Float? = 0.5f,
    ) = PodDevice(
        profileId = "test-profile",
        ble = mockDualPod(leftBattery, rightBattery, caseBattery),
        aap = null,
    )

    @Nested
    inner class Creates {

        @Test
        fun `creates cached state from live device with battery`() {
            val result = liveDevice().toCachedState(existing = null, now = now)
            result.shouldNotBeNull()
            result.profileId shouldBe "test-profile"
            result.left?.percent shouldBe 0.8f
            result.right?.percent shouldBe 0.7f
            result.case?.percent shouldBe 0.5f
            result.lastSeenAt shouldBe now
        }

        @Test
        fun `preserves existing slots when live value is null`() {
            val existing = CachedDeviceState(
                profileId = "test-profile",
                model = PodModel.AIRPODS_PRO3,
                left = CachedDeviceState.CachedBatterySlot(0.9f, now.minusSeconds(60)),
                lastSeenAt = now.minusSeconds(120),
            )
            val result = liveDevice(leftBattery = null, caseBattery = 0.6f).toCachedState(existing, now)
            result.shouldNotBeNull()
            result.left?.percent shouldBe 0.9f
            result.case?.percent shouldBe 0.6f
        }
    }

    @Nested
    inner class Skips {

        @Test
        fun `returns null for cached-only device`() {
            val device = PodDevice(profileId = "test-profile", ble = null, aap = null, cached = mockk(relaxed = true))
            device.toCachedState(existing = null, now = now).shouldBeNull()
        }

        @Test
        fun `returns null when no profile`() {
            val device = PodDevice(profileId = null, ble = mockDualPod(caseBattery = 0.5f), aap = null)
            device.toCachedState(existing = null, now = now).shouldBeNull()
        }

        @Test
        fun `returns null when all battery values null`() {
            liveDevice(leftBattery = null, rightBattery = null, caseBattery = null)
                .toCachedState(existing = null, now = now)
                .shouldBeNull()
        }

        @Test
        fun `returns null when state unchanged`() {
            val existing = CachedDeviceState(
                profileId = "test-profile",
                model = PodModel.AIRPODS_PRO3,
                left = CachedDeviceState.CachedBatterySlot(0.8f, now),
                right = CachedDeviceState.CachedBatterySlot(0.7f, now),
                case = CachedDeviceState.CachedBatterySlot(0.5f, now),
                isLeftCharging = false,
                isRightCharging = false,
                isCaseCharging = false,
                isHeadsetCharging = false,
                lastSeenAt = now,
            )
            liveDevice().toCachedState(existing, now).shouldBeNull()
        }

        @Test
        fun `returns new state when battery changed`() {
            val existing = CachedDeviceState(
                profileId = "test-profile",
                model = PodModel.AIRPODS_PRO3,
                left = CachedDeviceState.CachedBatterySlot(0.9f, now),
                lastSeenAt = now,
            )
            liveDevice(leftBattery = 0.8f).toCachedState(existing, now).shouldNotBeNull()
        }

        @Test
        fun `returns new state when lastSeenAt drifted over 1 minute`() {
            val existing = CachedDeviceState(
                profileId = "test-profile",
                model = PodModel.AIRPODS_PRO3,
                left = CachedDeviceState.CachedBatterySlot(0.8f, now.minusSeconds(120)),
                right = CachedDeviceState.CachedBatterySlot(0.7f, now.minusSeconds(120)),
                case = CachedDeviceState.CachedBatterySlot(0.5f, now.minusSeconds(120)),
                isLeftCharging = false,
                isRightCharging = false,
                isCaseCharging = false,
                isHeadsetCharging = false,
                lastSeenAt = now.minusSeconds(120),
            )
            liveDevice().toCachedState(existing, now).shouldNotBeNull()
        }
    }
}
