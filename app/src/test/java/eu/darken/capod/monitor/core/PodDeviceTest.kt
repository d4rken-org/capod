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
}
