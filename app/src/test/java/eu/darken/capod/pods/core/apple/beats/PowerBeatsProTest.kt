package eu.darken.capod.pods.core.apple.beats

import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.BaseAirPodsTest
import eu.darken.capod.pods.core.apple.HasAppleColor
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class PowerBeatsProTest : BaseAirPodsTest() {

    @Test
    fun `test PowerBeatsPro`() = runTest {
        create<PowerBeatsPro>("07 19 01 0B 20 54 AA B5 31 00 00 E0 0C A7 8A 60 4B D3 7D F4 60 4F 2C 73 E9 A7 F4") {
            pubPrefix shouldBe 0x01.toUByte()
            pubDeviceModel shouldBe 0x0B20.toUShort()
            pubStatus shouldBe 0x54.toUByte()
            pubPodsBattery shouldBe 0xAA.toUByte()
            pubFlags shouldBe 0xB.toUShort()
            pubCaseBattery shouldBe 0x5.toUShort()
            pubCaseLidState shouldBe 0x31.toUByte()
            pubDeviceColor shouldBe 0x00.toUByte()
            pubSuffix shouldBe 0x00.toUByte()

            isLeftPodMicrophone shouldBe true
            isRightPodMicrophone shouldBe false

            batteryLeftPodPercent shouldBe 1.0f
            batteryRightPodPercent shouldBe 1.0f

            isCaseCharging shouldBe false
            isRightPodCharging shouldBe true
            isLeftPodCharging shouldBe true
            batteryCasePercent shouldBe 0.5f

            podStyle.identifier shouldBe HasAppleColor.DeviceColor.WHITE.name

            model shouldBe PodDevice.Model.POWERBEATS_PRO
        }
    }

    // Via https://github.com/d4rken-org/capod/pull/303#issuecomment-2991876052
    @Test
    fun `test PowerBeatsPro - variant 2`() = runTest {
        create<PowerBeatsPro>("07 19 01 0B 20 21 AA 8F 02 44 24 6D 3E CD 38 A6 F9 6C 6A EC 95 65 AF 97 08 95 49") {
            pubPrefix shouldBe 0x01.toUByte()
            pubDeviceModel shouldBe 0x0B20.toUShort()
            pubStatus shouldBe 0x21.toUByte()
            pubPodsBattery shouldBe 0xAA.toUByte()
            pubFlags shouldBe 0x8.toUShort()
            pubCaseBattery shouldBe 0xF.toUShort()
            pubCaseLidState shouldBe 0x02.toUByte()
            pubDeviceColor shouldBe 0x44.toUByte()
            pubSuffix shouldBe 0x24.toUByte()

            isLeftPodMicrophone shouldBe true
            isRightPodMicrophone shouldBe false

            batteryLeftPodPercent shouldBe 1.0f
            batteryRightPodPercent shouldBe 1.0f

            isCaseCharging shouldBe false
            isRightPodCharging shouldBe false
            isLeftPodCharging shouldBe false
            batteryCasePercent shouldBe null

            podStyle.identifier shouldBe HasAppleColor.DeviceColor.UNKNOWN.name

            model shouldBe PodDevice.Model.POWERBEATS_PRO
        }
    }
}