package eu.darken.capod.monitor.core

import eu.darken.capod.pods.core.PodDevice
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

class MonitoredDeviceTest : BaseTest() {

    private fun mockDualPod(
        model: PodDevice.Model = PodDevice.Model.AIRPODS_PRO3,
        leftBattery: Float? = 0.8f,
        rightBattery: Float? = 0.9f,
        caseBattery: Float? = 0.5f,
    ): PodDevice {
        // DualApplePods implements DualPodDevice + HasCase + other interfaces
        return mockk<DualApplePods>(relaxed = true) {
            every { this@mockk.model } returns model
            every { batteryLeftPodPercent } returns leftBattery
            every { batteryRightPodPercent } returns rightBattery
            every { batteryCasePercent } returns caseBattery
        }
    }

    @Test
    fun `BLE-only device exposes battery from BLE`() {
        val device = MonitoredDevice(ble = mockDualPod(leftBattery = 0.8f), aap = null)
        device.batteryLeft shouldBe 0.8f
        device.isAapConnected shouldBe false
    }

    @Test
    fun `capabilities come from model features`() {
        val device = MonitoredDevice(
            ble = mockDualPod(model = PodDevice.Model.AIRPODS_PRO3),
            aap = null,
        )
        device.hasDualPods shouldBe true
        device.hasCase shouldBe true
        device.hasEarDetection shouldBe true
        device.hasAncControl shouldBe true
    }

    @Test
    fun `Beats Solo 3 has no dual pods or case`() {
        val device = MonitoredDevice(
            ble = mockk(relaxed = true) { every { model } returns PodDevice.Model.BEATS_SOLO_3 },
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
        val device = MonitoredDevice(ble = mockDualPod(), aap = aap)
        device.isAapConnected shouldBe true
        device.ancMode.shouldNotBeNull()
        device.ancMode!!.current shouldBe AncModeValue.TRANSPARENCY
    }

    @Test
    fun `ANC mode is null when not AAP connected`() {
        val device = MonitoredDevice(ble = mockDualPod(), aap = null)
        device.ancMode.shouldBeNull()
    }

    @Test
    fun `null BLE gives UNKNOWN model`() {
        val device = MonitoredDevice(ble = null, aap = null)
        device.model shouldBe PodDevice.Model.UNKNOWN
    }
}
