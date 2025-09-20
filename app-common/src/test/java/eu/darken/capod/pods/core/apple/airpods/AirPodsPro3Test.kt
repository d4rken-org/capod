package eu.darken.capod.pods.core.apple.airpods

import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.BaseAirPodsTest
import eu.darken.capod.pods.core.apple.HasAppleColor
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class AirPodsPro3Test : BaseAirPodsTest() {

    /**
     * Test case for AirPods Pro 3 - placeholder with correct device code
     * Following the pattern of other AirPods models (0x2720)
     */
    @Test
    fun `AirPods Pro 3 - placeholder test`() = runTest {
        // This test uses a placeholder hex string with the correct device code 0x2720
        // The actual proximity pairing data will need to be captured from real AirPods Pro 3
        create<AirPodsPro3>("07 19 01 27 20 0B 99 8F 11 00 04 BD A7 3B FF 2D 8A 3C AF 9B 1A 7C 74 B7 A9 D1 C3") {

            pubPrefix shouldBe 0x01.toUByte()
            pubDeviceModel shouldBe 0x2720.toUShort()
            pubStatus shouldBe 0x0B.toUByte()
            pubPodsBattery shouldBe 0x99.toUByte()
            pubFlags shouldBe 0x8.toUShort()
            pubCaseBattery shouldBe 0xF.toUShort()
            pubCaseLidState shouldBe 0x11.toUByte()
            pubDeviceColor shouldBe 0x0.toUByte()
            pubSuffix shouldBe 0x04.toUByte()

            isLeftPodMicrophone shouldBe false
            isRightPodMicrophone shouldBe true

            isLeftPodInEar shouldBe true
            isRightPodInEar shouldBe true

            batteryLeftPodPercent shouldBe 0.9f
            batteryRightPodPercent shouldBe 0.9f

            isCaseCharging shouldBe false
            isRightPodCharging shouldBe false
            isLeftPodCharging shouldBe false
            batteryCasePercent shouldBe null

            podStyle.identifier shouldBe HasAppleColor.DeviceColor.WHITE.name

            model shouldBe PodDevice.Model.AIRPODS_PRO3
        }
    }

    @Test
    fun `AirPods Pro 3 - placeholder test - in case`() = runTest {
        // This test uses a placeholder hex string with the correct device code 0x2720
        // The actual proximity pairing data will need to be captured from real AirPods Pro 3
        create<AirPodsPro3>("07 19 01 27 20 53 AA 98 32 00 05 49 0A B8 BF 8E 29 D8 70 12 0D A7 0C CE 77 56 00") {

            isLeftPodMicrophone shouldBe true
            isRightPodMicrophone shouldBe false

            isLeftPodInEar shouldBe true
            isRightPodInEar shouldBe false

            batteryLeftPodPercent shouldBe 1f
            batteryRightPodPercent shouldBe 1f

            isCaseCharging shouldBe false
            isRightPodCharging shouldBe true
            isLeftPodCharging shouldBe false
            batteryCasePercent shouldBe 0.8f

            podStyle.identifier shouldBe HasAppleColor.DeviceColor.WHITE.name

            model shouldBe PodDevice.Model.AIRPODS_PRO3
        }
    }
}
