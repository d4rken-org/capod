package eu.darken.capod.pods.core.apple.airpods

import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.BaseAirPodsTest
import eu.darken.capod.pods.core.apple.HasAppleColor
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class AirPodsPro2Test : BaseAirPodsTest() {

    /**
     * https://github.com/d4rken-org/capod/issues/31#issuecomment-1256791084
     */
    @Test
    fun `test AirPods Pro 2 - unknown setup from #31`() = runTest {
        create<AirPodsPro2>("07 19 01 14 20 55 88 F9 51 00 04 20 50 03 CA D5 C9 AC 0F FA 84 78 94 5A 4D DF F5") {

            pubPrefix shouldBe 0x01.toUByte()
            pubDeviceModel shouldBe 0x1420.toUShort()
            pubStatus shouldBe 0x55.toUByte()
            pubPodsBattery shouldBe 0x88.toUByte()
            pubFlags shouldBe 0xF.toUShort()
            pubCaseBattery shouldBe 0x9.toUShort()
            pubCaseLidState shouldBe 0x51.toUByte()
            pubDeviceColor shouldBe 0x0.toUByte()
            pubSuffix shouldBe 0x04.toUByte()

            isLeftPodMicrophone shouldBe true
            isRightPodMicrophone shouldBe false

            isLeftPodInEar shouldBe false
            isRightPodInEar shouldBe false

            batteryLeftPodPercent shouldBe 0.8f
            batteryRightPodPercent shouldBe 0.8f

            isCaseCharging shouldBe true
            isRightPodCharging shouldBe true
            isLeftPodCharging shouldBe true
            batteryCasePercent shouldBe 0.9f

            podStyle.identifier shouldBe HasAppleColor.DeviceColor.WHITE.name

            model shouldBe PodDevice.Model.AIRPODS_PRO2
        }
    }

    /**
     * https://old.reddit.com/message/messages/1hst12h
     */
    @Test
    fun `test AirPods Pro 2 - unknown setup from reddit user`() = runTest {
        create<AirPodsPro2>("07 19 01 14 20 2B 9A 8F 01 00 04 0F 26 1A C4 2B FA 2F B9 B6 08 CD 60 CB DF 75 AB") {

            pubPrefix shouldBe 0x01.toUByte()
            pubDeviceModel shouldBe 0x1420.toUShort()
            pubStatus shouldBe 0x2B.toUByte()
            pubPodsBattery shouldBe 0x9A.toUByte()
            pubFlags shouldBe 0x8.toUShort()
            pubCaseBattery shouldBe 0xF.toUShort()
            pubCaseLidState shouldBe 0x01.toUByte()
            pubDeviceColor shouldBe 0x0.toUByte()
            pubSuffix shouldBe 0x04.toUByte()

            isLeftPodMicrophone shouldBe true
            isRightPodMicrophone shouldBe false

            isLeftPodInEar shouldBe true
            isRightPodInEar shouldBe true

            batteryLeftPodPercent shouldBe 1.0f
            batteryRightPodPercent shouldBe 0.9f

            isCaseCharging shouldBe false
            isRightPodCharging shouldBe false
            isLeftPodCharging shouldBe false
            batteryCasePercent shouldBe null

            podStyle.identifier shouldBe HasAppleColor.DeviceColor.WHITE.name
        }
    }
}