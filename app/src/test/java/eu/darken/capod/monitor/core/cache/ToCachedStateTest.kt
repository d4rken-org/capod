package eu.darken.capod.monitor.core.cache

import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.AapPodState
import eu.darken.capod.pods.core.apple.aap.protocol.AapDeviceInfo
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

        @Test
        fun `returns new state when a live slot refreshes an old cached timestamp`() {
            val existing = CachedDeviceState(
                profileId = "test-profile",
                model = PodModel.AIRPODS_PRO3,
                left = CachedDeviceState.CachedBatterySlot(0.8f, now.minusSeconds(10)),
                right = CachedDeviceState.CachedBatterySlot(0.7f, now.minusSeconds(10)),
                case = CachedDeviceState.CachedBatterySlot(0.5f, now.minusSeconds(177 * 60 * 60)),
                isLeftCharging = false,
                isRightCharging = false,
                isCaseCharging = false,
                isHeadsetCharging = false,
                lastSeenAt = now.minusSeconds(10),
            )

            liveDevice(leftBattery = 0.8f, rightBattery = 0.7f, caseBattery = 0.5f)
                .toCachedState(existing, now)
                .shouldNotBeNull()
        }

        @Test
        fun `returns new state when only earbud serial changes`() {
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
                deviceName = "AirPods",
                serialNumber = "",
                firmwareVersion = "",
                leftEarbudSerial = "OLD-LEFT",
                rightEarbudSerial = "R-1",
                marketingVersion = "1234",
                lastSeenAt = now,
            )
            val device = PodDevice(
                profileId = "test-profile",
                ble = mockDualPod(leftBattery = 0.8f, rightBattery = 0.7f, caseBattery = 0.5f),
                aap = AapPodState(
                    deviceInfo = deviceInfo(
                        leftEarbudSerial = "NEW-LEFT",
                        rightEarbudSerial = "R-1",
                        marketingVersion = "1234",
                    ),
                ),
            )

            val result = device.toCachedState(existing, now).shouldNotBeNull()
            result.leftEarbudSerial shouldBe "NEW-LEFT"
            result.rightEarbudSerial shouldBe "R-1"
            result.marketingVersion shouldBe "1234"
        }

        @Test
        fun `returns new state when only marketing version changes`() {
            val existing = CachedDeviceState(
                profileId = "test-profile",
                model = PodModel.AIRPODS_PRO3,
                left = CachedDeviceState.CachedBatterySlot(0.8f, now),
                isLeftCharging = false,
                isRightCharging = false,
                isCaseCharging = false,
                isHeadsetCharging = false,
                deviceName = "AirPods",
                serialNumber = "",
                firmwareVersion = "",
                marketingVersion = "1234",
                lastSeenAt = now,
            )
            val device = PodDevice(
                profileId = "test-profile",
                ble = mockDualPod(leftBattery = 0.8f),
                aap = AapPodState(deviceInfo = deviceInfo(marketingVersion = "5678")),
            )

            val result = device.toCachedState(existing, now).shouldNotBeNull()
            result.marketingVersion shouldBe "5678"
        }
    }

    @Nested
    inner class DeviceInfoOnly {

        @Test
        fun `persists DeviceInfo when no live battery is available`() {
            // Mirror how DeviceMonitor.aapOnlyForPersistence builds an AAP-only PodDevice:
            // profileModel and profileAddress carry the model/address since `ble` is null.
            val device = PodDevice(
                profileId = "test-profile",
                ble = null,
                aap = AapPodState(
                    deviceInfo = deviceInfo(
                        name = "Pro 3",
                        leftEarbudSerial = "L-1",
                        rightEarbudSerial = "R-1",
                        marketingVersion = "9999",
                    ),
                ),
                profileModel = PodModel.AIRPODS_PRO3,
                profileAddress = "AA:BB:CC:DD:EE:FF",
            )

            val result = device.toCachedState(existing = null, now = now).shouldNotBeNull()
            result.model shouldBe PodModel.AIRPODS_PRO3
            result.address shouldBe "AA:BB:CC:DD:EE:FF"
            result.deviceName shouldBe "Pro 3"
            result.leftEarbudSerial shouldBe "L-1"
            result.rightEarbudSerial shouldBe "R-1"
            result.marketingVersion shouldBe "9999"
        }

        @Test
        fun `DeviceInfo-only update preserves existing slot timestamps (no live refresh)`() {
            // Regression: if toCachedState consults the unified PodDevice.batteryX getter (which
            // falls back to cache), an AAP-only DeviceInfo update would re-stamp every slot with
            // `now`, refreshing stale cached readings indefinitely. Raw live extraction prevents
            // that.
            val oldStamp = now.minusSeconds(3600)
            val existing = CachedDeviceState(
                profileId = "test-profile",
                model = PodModel.AIRPODS_PRO3,
                left = CachedDeviceState.CachedBatterySlot(0.8f, oldStamp),
                right = CachedDeviceState.CachedBatterySlot(0.7f, oldStamp),
                case = CachedDeviceState.CachedBatterySlot(0.5f, oldStamp),
                marketingVersion = "OLD",
                lastSeenAt = oldStamp,
            )
            val device = PodDevice(
                profileId = "test-profile",
                ble = null,
                aap = AapPodState(deviceInfo = deviceInfo(marketingVersion = "NEW")),
                profileModel = PodModel.AIRPODS_PRO3,
            )

            val result = device.toCachedState(existing, now).shouldNotBeNull()
            result.marketingVersion shouldBe "NEW"
            result.left?.updatedAt shouldBe oldStamp
            result.right?.updatedAt shouldBe oldStamp
            result.case?.updatedAt shouldBe oldStamp
            result.left?.percent shouldBe 0.8f
        }
    }

    private fun deviceInfo(
        name: String = "AirPods",
        serialNumber: String = "",
        firmwareVersion: String = "",
        leftEarbudSerial: String? = null,
        rightEarbudSerial: String? = null,
        marketingVersion: String? = null,
    ) = AapDeviceInfo(
        name = name,
        modelNumber = "",
        manufacturer = "",
        serialNumber = serialNumber,
        firmwareVersion = firmwareVersion,
        leftEarbudSerial = leftEarbudSerial,
        rightEarbudSerial = rightEarbudSerial,
        marketingVersion = marketingVersion,
    )
}
