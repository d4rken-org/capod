package eu.darken.capod.monitor.core

import eu.darken.capod.pods.core.apple.ble.BlePodSnapshot
import eu.darken.capod.pods.core.apple.ble.devices.HasCase
import eu.darken.capod.pods.core.apple.ble.devices.HasChargeDetectionDual
import eu.darken.capod.pods.core.apple.ble.devices.HasEarDetection
import eu.darken.capod.pods.core.apple.ble.devices.HasEarDetectionDual
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.ble.devices.DualApplePods

import eu.darken.capod.pods.core.apple.aap.AapPodState
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import eu.darken.capod.pods.core.apple.ble.devices.ApplePods
import eu.darken.capod.pods.core.apple.ble.protocol.ProximityPayload
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant

class PodDeviceTest : BaseTest() {

    private fun mockDualPod(
        model: PodModel = PodModel.AIRPODS_PRO3,
        leftBattery: Float? = 0.8f,
        rightBattery: Float? = 0.9f,
        caseBattery: Float? = 0.5f,
    ): BlePodSnapshot {
        // DualApplePods implements DualBlePodSnapshot + HasCase + other interfaces
        return mockk<DualApplePods>(relaxed = true) {
            every { this@mockk.model } returns model
            every { batteryLeftPodPercent } returns leftBattery
            every { batteryRightPodPercent } returns rightBattery
            every { batteryCasePercent } returns caseBattery
        }
    }

    @Test
    fun `BLE-only device exposes battery from BLE`() {
        val device = PodDevice(ble = mockDualPod(leftBattery = 0.8f), aap = null)
        device.batteryLeft shouldBe 0.8f
        device.isAapConnected shouldBe false
    }

    @Test
    fun `capabilities come from model features`() {
        val device = PodDevice(
            ble = mockDualPod(model = PodModel.AIRPODS_PRO3),
            aap = null,
        )
        device.hasDualPods shouldBe true
        device.hasCase shouldBe true
        device.hasEarDetection shouldBe true
        device.hasAncControl shouldBe true
    }

    @Test
    fun `Beats Solo 3 has no dual pods or case`() {
        val device = PodDevice(
            ble = mockk(relaxed = true) { every { model } returns PodModel.BEATS_SOLO_3 },
            aap = null,
        )
        device.hasDualPods shouldBe false
        device.hasCase shouldBe false
    }

    @Test
    fun `AAP settings are available when connected`() {
        val aap = AapPodState(
            connectionState = AapPodState.ConnectionState.READY,
            settings = mapOf(
                AapSetting.AncMode::class to AapSetting.AncMode(
                    AapSetting.AncMode.Value.TRANSPARENCY,
                    listOf(AapSetting.AncMode.Value.ON, AapSetting.AncMode.Value.TRANSPARENCY, AapSetting.AncMode.Value.ADAPTIVE),
                ),
            ),
        )
        val device = PodDevice(ble = mockDualPod(), aap = aap)
        device.isAapConnected shouldBe true
        device.ancMode.shouldNotBeNull()
        device.ancMode!!.current shouldBe AapSetting.AncMode.Value.TRANSPARENCY
    }

    @Test
    fun `ANC mode is null when not AAP connected`() {
        val device = PodDevice(ble = mockDualPod(), aap = null)
        device.ancMode.shouldBeNull()
    }

    @Test
    fun `null BLE gives UNKNOWN model`() {
        val device = PodDevice(ble = null, aap = null)
        device.model shouldBe PodModel.UNKNOWN
    }

    @Test
    fun `identity properties delegate to BLE`() {
        val id = BlePodSnapshot.Id()
        val meta = mockk<BlePodSnapshot.Meta>(relaxed = true)
        val device = PodDevice(
            ble = mockk(relaxed = true) {
                every { identifier } returns id
                every { this@mockk.meta } returns meta
            },
            aap = null,
        )
        device.identifier shouldBe id
        device.meta shouldBe meta
    }

    @Test
    fun `identity properties null when BLE null`() {
        val device = PodDevice(ble = null, aap = null)
        device.identifier.shouldBeNull()
        device.meta.shouldBeNull()
    }

    @Test
    fun `signal timing properties delegate to BLE`() {
        val now = Instant.now()
        val earlier = now.minusSeconds(60)
        val device = PodDevice(
            ble = mockk(relaxed = true) {
                every { seenLastAt } returns now
                every { seenFirstAt } returns earlier
                every { signalQuality } returns 0.75f
                every { rssi } returns -50
            },
            aap = null,
        )
        device.seenLastAt shouldBe now
        device.seenFirstAt shouldBe earlier
        device.signalQuality shouldBe 0.75f
        device.rssi shouldBe -50
    }

    @Test
    fun `signal timing defaults when BLE null`() {
        val device = PodDevice(ble = null, aap = null)
        device.seenLastAt.shouldBeNull()
        device.seenFirstAt.shouldBeNull()
        device.signalQuality shouldBe 0f
        device.rssi shouldBe 0
    }

    @Test
    fun `charging properties delegate to BLE interfaces`() {
        val mock = mockk<DualApplePods>(relaxed = true) {
            every { model } returns PodModel.AIRPODS_PRO3
            every { (this@mockk as HasChargeDetectionDual).isLeftPodCharging } returns true
            every { (this@mockk as HasChargeDetectionDual).isRightPodCharging } returns false
            every { (this@mockk as HasCase).isCaseCharging } returns true
        }
        val device = PodDevice(ble = mock, aap = null)
        device.isLeftPodCharging shouldBe true
        device.isRightPodCharging shouldBe false
        device.isCaseCharging shouldBe true
    }

    @Test
    fun `ear detection properties delegate to BLE interfaces`() {
        val mock = mockk<DualApplePods>(relaxed = true) {
            every { model } returns PodModel.AIRPODS_PRO3
            every { (this@mockk as HasEarDetectionDual).isLeftPodInEar } returns true
            every { (this@mockk as HasEarDetectionDual).isRightPodInEar } returns false
            every { (this@mockk as HasEarDetection).isBeingWorn } returns false
            every { (this@mockk as HasEarDetectionDual).isEitherPodInEar } returns true
        }
        val device = PodDevice(ble = mock, aap = null)
        device.isLeftInEar shouldBe true
        device.isRightInEar shouldBe false
        device.isBeingWorn shouldBe false
        device.isEitherPodInEar shouldBe true
    }

    @Test
    fun `icon and label properties delegate to BLE`() {
        val device = PodDevice(
            ble = mockk(relaxed = true) {
                every { model } returns PodModel.AIRPODS_PRO3
                every { iconRes } returns 42
            },
            aap = null,
        )
        device.iconRes shouldBe 42
    }

    @Test
    fun `rawDataHex empty when BLE null`() {
        val device = PodDevice(ble = null, aap = null)
        device.rawDataHex shouldBe emptyList()
    }

    @Test
    fun `battery falls back to BLE when AAP battery is null`() {
        val aap = AapPodState(connectionState = AapPodState.ConnectionState.READY)
        val device = PodDevice(ble = mockDualPod(leftBattery = 0.8f), aap = aap)
        device.batteryLeft shouldBe 0.8f
        device.isAapConnected shouldBe true
    }

    @Test
    fun `AAP battery preferred over BLE battery`() {
        val aap = AapPodState(
            connectionState = AapPodState.ConnectionState.READY,
            batteries = mapOf(
                AapPodState.BatteryType.LEFT to AapPodState.Battery(AapPodState.BatteryType.LEFT, 0.79f, AapPodState.ChargingState.NOT_CHARGING),
            ),
        )
        val device = PodDevice(ble = mockDualPod(leftBattery = 0.8f), aap = aap)
        device.batteryLeft shouldBe 0.79f  // AAP 1% granularity wins over BLE 10%
    }

    @Test
    fun `AAP charging preferred over BLE charging`() {
        val mock = mockk<DualApplePods>(relaxed = true) {
            every { model } returns PodModel.AIRPODS_PRO3
            every { (this@mockk as HasChargeDetectionDual).isLeftPodCharging } returns false
        }
        val aap = AapPodState(
            connectionState = AapPodState.ConnectionState.READY,
            batteries = mapOf(
                AapPodState.BatteryType.LEFT to AapPodState.Battery(AapPodState.BatteryType.LEFT, 0.8f, AapPodState.ChargingState.CHARGING_OPTIMIZED),
            ),
        )
        val device = PodDevice(ble = mock, aap = aap)
        device.isLeftPodCharging shouldBe true  // AAP CHARGING_OPTIMIZED counts as charging
    }

    @Test
    fun `address returns bonded address from profile, not BLE RPA`() {
        val bondedAddress = "CC:22:FE:25:69:63"
        val bleRpa = "5A:3B:1C:2D:4E:6F"
        val profile = mockk<eu.darken.capod.profiles.core.AppleDeviceProfile>(relaxed = true) {
            every { address } returns bondedAddress
        }
        val ble = mockk<DualApplePods>(relaxed = true) {
            every { model } returns PodModel.AIRPODS_PRO3
            every { this@mockk.address } returns bleRpa
            every { meta } returns ApplePods.AppleMeta(profile = profile)
        }
        val device = PodDevice(ble = ble, aap = null)
        device.address shouldBe bondedAddress
        device.bleAddress shouldBe bleRpa
    }

    // --- AAP signal quality boost tests ---

    private fun deviceWithBleQuality(bleQuality: Float, aap: AapPodState? = null): PodDevice {
        val ble = mockk<DualApplePods>(relaxed = true) {
            every { model } returns PodModel.AIRPODS_PRO3
            every { signalQuality } returns bleQuality
        }
        return PodDevice(ble = ble, aap = aap)
    }

    @Test
    fun `AAP boost - READY with fresh message applies 0_15`() {
        val now = Instant.parse("2026-01-01T12:00:00Z")
        val aap = AapPodState(
            connectionState = AapPodState.ConnectionState.READY,
            lastMessageAt = now.minusSeconds(5),
        )
        val device = deviceWithBleQuality(0.50f, aap)
        device.computeAapBoost(now) shouldBe 0.15f
    }

    @Test
    fun `AAP boost - READY with warm message applies 0_10`() {
        val now = Instant.parse("2026-01-01T12:00:00Z")
        val aap = AapPodState(
            connectionState = AapPodState.ConnectionState.READY,
            lastMessageAt = now.minusSeconds(20),
        )
        val device = deviceWithBleQuality(0.50f, aap)
        device.computeAapBoost(now) shouldBe 0.10f
    }

    @Test
    fun `AAP boost - READY with stale message applies 0_05`() {
        val now = Instant.parse("2026-01-01T12:00:00Z")
        val aap = AapPodState(
            connectionState = AapPodState.ConnectionState.READY,
            lastMessageAt = now.minusSeconds(60),
        )
        val device = deviceWithBleQuality(0.50f, aap)
        device.computeAapBoost(now) shouldBe 0.05f
    }

    @Test
    fun `AAP boost - boundary at exactly 10s is warm tier`() {
        val now = Instant.parse("2026-01-01T12:00:00Z")
        val aap = AapPodState(
            connectionState = AapPodState.ConnectionState.READY,
            lastMessageAt = now.minusSeconds(10),
        )
        val device = deviceWithBleQuality(0.50f, aap)
        device.computeAapBoost(now) shouldBe 0.10f
    }

    @Test
    fun `AAP boost - boundary at exactly 30s is stale tier`() {
        val now = Instant.parse("2026-01-01T12:00:00Z")
        val aap = AapPodState(
            connectionState = AapPodState.ConnectionState.READY,
            lastMessageAt = now.minusSeconds(30),
        )
        val device = deviceWithBleQuality(0.50f, aap)
        device.computeAapBoost(now) shouldBe 0.05f
    }

    @Test
    fun `AAP boost - signalQuality clamps to 1_0`() {
        val now = Instant.parse("2026-01-01T12:00:00Z")
        val aap = AapPodState(
            connectionState = AapPodState.ConnectionState.READY,
            lastMessageAt = now.minusSeconds(5),
        )
        val device = deviceWithBleQuality(0.95f, aap)
        device.signalQuality shouldBe 1.0f
    }

    @Test
    fun `AAP boost - CONNECTING state applies 0_05`() {
        val now = Instant.parse("2026-01-01T12:00:00Z")
        val aap = AapPodState(connectionState = AapPodState.ConnectionState.CONNECTING)
        val device = deviceWithBleQuality(0.50f, aap)
        device.computeAapBoost(now) shouldBe 0.05f
    }

    @Test
    fun `AAP boost - future timestamp treated as stale`() {
        val now = Instant.parse("2026-01-01T12:00:00Z")
        val aap = AapPodState(
            connectionState = AapPodState.ConnectionState.READY,
            lastMessageAt = now.plusSeconds(60),
        )
        val device = deviceWithBleQuality(0.50f, aap)
        device.computeAapBoost(now) shouldBe 0.05f
    }

    @Test
    fun `AAP boost - READY with null lastMessageAt returns 0_05`() {
        val now = Instant.parse("2026-01-01T12:00:00Z")
        val aap = AapPodState(connectionState = AapPodState.ConnectionState.READY)
        val device = deviceWithBleQuality(0.50f, aap)
        device.computeAapBoost(now) shouldBe 0.05f
    }

    @Test
    fun `AAP boost - no AAP returns zero`() {
        val now = Instant.parse("2026-01-01T12:00:00Z")
        val device = deviceWithBleQuality(0.50f, aap = null)
        device.computeAapBoost(now) shouldBe 0f
    }

    // --- bleKeyState tests ---

    @Test
    fun `bleKeyState - null BLE returns NONE`() {
        val device = PodDevice(ble = null, aap = null)
        device.bleKeyState shouldBe BleKeyState.NONE
    }

    @Test
    fun `bleKeyState - non-Apple BLE returns NONE`() {
        val device = PodDevice(ble = mockk(relaxed = true) { every { model } returns PodModel.UNKNOWN }, aap = null)
        device.bleKeyState shouldBe BleKeyState.NONE
    }

    @Test
    fun `bleKeyState - Apple BLE without IRK match returns NONE`() {
        val ble = mockk<DualApplePods>(relaxed = true) {
            every { model } returns PodModel.AIRPODS_PRO3
            every { meta } returns ApplePods.AppleMeta(isIRKMatch = false)
            every { payload } returns ProximityPayload(public = ProximityPayload.Public(UByteArray(9)), private = null)
        }
        val device = PodDevice(ble = ble, aap = null)
        device.bleKeyState shouldBe BleKeyState.NONE
    }

    @Test
    fun `bleKeyState - IRK match without private payload returns IRK_ONLY`() {
        val ble = mockk<DualApplePods>(relaxed = true) {
            every { model } returns PodModel.AIRPODS_PRO3
            every { meta } returns ApplePods.AppleMeta(isIRKMatch = true)
            every { payload } returns ProximityPayload(public = ProximityPayload.Public(UByteArray(9)), private = null)
        }
        val device = PodDevice(ble = ble, aap = null)
        device.bleKeyState shouldBe BleKeyState.IRK_ONLY
    }

    @Test
    fun `bleKeyState - IRK match with private payload returns IRK_AND_ENCRYPTED`() {
        val ble = mockk<DualApplePods>(relaxed = true) {
            every { model } returns PodModel.AIRPODS_PRO3
            every { meta } returns ApplePods.AppleMeta(isIRKMatch = true)
            every { payload } returns ProximityPayload(
                public = ProximityPayload.Public(UByteArray(9)),
                private = ProximityPayload.Private(UByteArray(8)),
            )
        }
        val device = PodDevice(ble = ble, aap = null)
        device.bleKeyState shouldBe BleKeyState.IRK_AND_ENCRYPTED
    }
}
