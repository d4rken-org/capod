package eu.darken.capod.monitor.core

import eu.darken.capod.monitor.core.cache.CachedDeviceState
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.AapPodState
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

class PodDeviceCacheTest : BaseTest() {

    private val fiveMinAgo = Instant.parse("2026-03-31T11:55:00Z")
    private val oneHourAgo = Instant.parse("2026-03-31T11:00:00Z")

    private val cachedState = CachedDeviceState(
        profileId = "test-profile",
        model = PodModel.AIRPODS_PRO3,
        address = "AA:BB:CC:DD:EE:FF",
        left = CachedDeviceState.CachedBatterySlot(0.8f, fiveMinAgo),
        right = CachedDeviceState.CachedBatterySlot(0.7f, fiveMinAgo),
        case = CachedDeviceState.CachedBatterySlot(0.5f, oneHourAgo),
        headset = null,
        isLeftCharging = false,
        isRightCharging = false,
        isCaseCharging = true,
        lastSeenAt = fiveMinAgo,
    )

    /**
     * DualApplePods extends DualBlePodSnapshot, HasCase, HasChargeDetectionDual, etc.
     * Using it as the mock type ensures all interface casts in PodDevice work correctly.
     */
    private fun mockDualPod(
        leftBattery: Float? = null,
        rightBattery: Float? = null,
        caseBattery: Float? = null,
    ): DualApplePods = mockk(relaxed = true) {
        every { batteryLeftPodPercent } returns leftBattery
        every { batteryRightPodPercent } returns rightBattery
        every { batteryCasePercent } returns caseBattery
        every { model } returns PodModel.AIRPODS_PRO3
    }

    @Nested
    inner class CacheFallback {

        @Test
        fun `battery falls back to cache when live sources are null`() {
            val device = PodDevice(profileId = "test-profile", ble = null, aap = null, cached = cachedState)
            device.batteryLeft shouldBe 0.8f
            device.batteryRight shouldBe 0.7f
            device.batteryCase shouldBe 0.5f
            device.batteryHeadset.shouldBeNull()
        }

        @Test
        fun `live BLE takes precedence over cache`() {
            val device = PodDevice(
                profileId = "test-profile", ble = mockDualPod(leftBattery = 0.9f, rightBattery = 0.6f, caseBattery = 0.3f),
                aap = null,
                cached = cachedState,
            )
            device.batteryLeft shouldBe 0.9f
            device.batteryRight shouldBe 0.6f
            device.batteryCase shouldBe 0.3f
        }

        @Test
        fun `live AAP takes precedence over both BLE and cache`() {
            val aapState = AapPodState(
                batteries = mapOf(
                    AapPodState.BatteryType.LEFT to AapPodState.Battery(AapPodState.BatteryType.LEFT, 0.95f, AapPodState.ChargingState.NOT_CHARGING),
                )
            )
            val device = PodDevice(
                profileId = "test-profile", ble = mockDualPod(leftBattery = 0.5f),
                aap = aapState,
                cached = cachedState,
            )
            device.batteryLeft shouldBe 0.95f  // AAP wins
            device.batteryRight shouldBe 0.7f  // BLE null -> cache
            device.batteryCase shouldBe 0.5f   // BLE null -> cache
        }

        @Test
        fun `charging falls back to cache`() {
            val device = PodDevice(profileId = "test-profile", ble = null, aap = null, cached = cachedState)
            device.isLeftPodCharging shouldBe false
            device.isCaseCharging shouldBe true
        }

        @Test
        fun `model falls back to cache`() {
            val device = PodDevice(profileId = "test-profile", ble = null, aap = null, cached = cachedState)
            device.model shouldBe PodModel.AIRPODS_PRO3
        }

        @Test
        fun `seenLastAt falls back to cache`() {
            val device = PodDevice(profileId = "test-profile", ble = null, aap = null, cached = cachedState)
            device.seenLastAt shouldBe fiveMinAgo
        }

        @Test
        fun `seenLastAt prefers live AAP message over stale cache`() {
            // Reproduces the reported bug: while AAP is connected, iOS throttles BLE advertising.
            // After BlePodMonitor evicts the BLE snapshot, the merged device had ble=null and the
            // getter fell through to the stale cache, showing "hours ago" even though AAP traffic
            // was still flowing. Live AAP messages must win over any cached timestamp.
            val freshAapMessage = Instant.parse("2026-03-31T11:59:55Z") // 5s ago, newer than cache
            val aap = AapPodState(
                connectionState = AapPodState.ConnectionState.READY,
                lastMessageAt = freshAapMessage,
            )
            val device = PodDevice(profileId = "test-profile", ble = null, aap = aap, cached = cachedState)
            device.seenLastAt shouldBe freshAapMessage
        }

        @Test
        fun `profileId falls back to cache`() {
            val device = PodDevice(profileId = "test-profile", ble = null, aap = null, cached = cachedState)
            device.profileId shouldBe "test-profile"
        }
    }

    @Nested
    inner class IsLive {

        @Test
        fun `isLive true when BLE present`() {
            val device = PodDevice(profileId = "test-profile", ble = mockDualPod(), aap = null, cached = cachedState)
            device.isLive shouldBe true
        }

        @Test
        fun `isLive true when AAP present`() {
            val device = PodDevice(profileId = "test-profile", ble = null, aap = AapPodState(), cached = cachedState)
            device.isLive shouldBe true
        }

        @Test
        fun `isLive false when only cache`() {
            val device = PodDevice(profileId = "test-profile", ble = null, aap = null, cached = cachedState)
            device.isLive shouldBe false
        }
    }

    @Nested
    inner class StalenessDetection {

        @Test
        fun `isBatteryCached true when all live null and cache has values`() {
            val device = PodDevice(profileId = "test-profile", ble = null, aap = null, cached = cachedState)
            device.isBatteryCached shouldBe true
        }

        @Test
        fun `isBatteryCached false when all live sources have data`() {
            val device = PodDevice(
                profileId = "test-profile", ble = mockDualPod(leftBattery = 0.9f, rightBattery = 0.8f, caseBattery = 0.3f),
                aap = null,
                cached = cachedState,
            )
            device.isBatteryCached shouldBe false
        }

        @Test
        fun `isBatteryCached true when BLE present but pod batteries are null`() {
            val device = PodDevice(
                profileId = "test-profile", ble = mockDualPod(caseBattery = 0.4f),
                aap = null,
                cached = cachedState,
            )
            device.isBatteryCached shouldBe true
        }

        @Test
        fun `isBatteryCached false when no cache`() {
            val device = PodDevice(profileId = "test-profile", ble = null, aap = null, cached = null)
            device.isBatteryCached shouldBe false
        }

        @Test
        fun `cachedBatteryAt returns oldest cached slot timestamp`() {
            val device = PodDevice(profileId = "test-profile", ble = null, aap = null, cached = cachedState)
            device.cachedBatteryAt shouldBe oneHourAgo
        }

        @Test
        fun `cachedBatteryAt null when live data covers all slots`() {
            val device = PodDevice(
                profileId = "test-profile", ble = mockDualPod(leftBattery = 0.9f, rightBattery = 0.8f, caseBattery = 0.3f),
                aap = null,
                cached = cachedState,
            )
            device.cachedBatteryAt.shouldBeNull()
        }

        @Test
        fun `cachedBatteryAt returns only timestamp of slots that fell through`() {
            val device = PodDevice(
                profileId = "test-profile", ble = mockDualPod(caseBattery = 0.4f),
                aap = null,
                cached = cachedState,
            )
            device.cachedBatteryAt.shouldNotBeNull()
            device.cachedBatteryAt shouldBe fiveMinAgo
        }
    }
}
