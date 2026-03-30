package eu.darken.capod.monitor.core

import eu.darken.capod.pods.core.BlePodSnapshot
import eu.darken.capod.pods.core.HasCase
import eu.darken.capod.pods.core.HasChargeDetectionDual
import eu.darken.capod.pods.core.HasEarDetection
import eu.darken.capod.pods.core.HasEarDetectionDual
import eu.darken.capod.pods.core.PodModel
import eu.darken.capod.pods.core.apple.DualApplePods
import eu.darken.capod.pods.core.apple.protocol.aap.AapConnectionState
import eu.darken.capod.pods.core.apple.protocol.aap.AapPodState
import eu.darken.capod.pods.core.apple.protocol.aap.AapSetting
import eu.darken.capod.pods.core.apple.protocol.aap.AncModeValue
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
            connectionState = AapConnectionState.READY,
            settings = mapOf(
                AapSetting.AncMode::class to AapSetting.AncMode(
                    AncModeValue.TRANSPARENCY,
                    listOf(AncModeValue.ON, AncModeValue.TRANSPARENCY, AncModeValue.ADAPTIVE),
                ),
            ),
        )
        val device = PodDevice(ble = mockDualPod(), aap = aap)
        device.isAapConnected shouldBe true
        device.ancMode.shouldNotBeNull()
        device.ancMode!!.current shouldBe AncModeValue.TRANSPARENCY
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
}
