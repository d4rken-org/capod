package eu.darken.capod.pods.core.apple.beats

import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.BaseAirPodsTest
import eu.darken.capod.pods.core.apple.HasAppleColor
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class PowerBeatsPro2Test : BaseAirPodsTest() {

    @Test
    fun `test PowerBeatsPro2`() = runTest {
        create<PowerBeatsPro2>("07 19 01 1D 20 2B AA 8F 01 02 04 30 42 6F CE 74 EE ED 56 AF 4E 31 16 9D 00 D7 DE") {
            pubPrefix shouldBe 0x01.toUByte()
            pubDeviceModel shouldBe 0x1D20.toUShort()
            pubStatus shouldBe 0x2B.toUByte()
            pubPodsBattery shouldBe 0xAA.toUByte()
            pubFlags shouldBe 0x8.toUShort()
            pubCaseBattery shouldBe 0xF.toUShort()
            pubCaseLidState shouldBe 0x01.toUByte()
            pubDeviceColor shouldBe 0x02.toUByte()
            pubSuffix shouldBe 0x04.toUByte()

            isLeftPodMicrophone shouldBe true
            isRightPodMicrophone shouldBe false

            batteryLeftPodPercent shouldBe 1.0f
            batteryRightPodPercent shouldBe 1.0f

            isCaseCharging shouldBe false
            isRightPodCharging shouldBe false
            isLeftPodCharging shouldBe false
            batteryCasePercent shouldBe null

            podStyle.identifier shouldBe HasAppleColor.DeviceColor.RED.name

            model shouldBe PodDevice.Model.POWERBEATS_PRO2
        }
    }

    @Test
    fun `test PowerBeatsPro2 - variant 2`() = runTest {
        create<PowerBeatsPro2>("07 19 01 1D 20 73 AA 98 32 02 04 7D 47 B0 15 DE 5F B6 6B CB 46 F9 16 5C 88 4F 4E") {
            isLeftPodMicrophone shouldBe false
            isRightPodMicrophone shouldBe true

            batteryLeftPodPercent shouldBe 1.0f
            batteryRightPodPercent shouldBe 1.0f

            isCaseCharging shouldBe false
            isRightPodCharging shouldBe false
            isLeftPodCharging shouldBe true
            batteryCasePercent shouldBe 0.8f

            podStyle.identifier shouldBe HasAppleColor.DeviceColor.RED.name

            model shouldBe PodDevice.Model.POWERBEATS_PRO2
        }
    }
}