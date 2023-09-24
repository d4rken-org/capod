package eu.darken.capod.pods.core.apple.airpods

import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.BaseAirPodsTest
import eu.darken.capod.pods.core.apple.HasAppleColor
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class AirPodsPro2UsbcTest : BaseAirPodsTest() {

    /**
     * https://github.com/d4rken-org/capod/issues/164
     */
    @Test
    fun `AirPods Pro 2 with USB-C  - via #164`() = runTest {
        create<AirPodsPro2Usbc>("07 19 01 24 20 0B 99 8F 11 00 04 BD A7 3B FF 2D 8A 3C AF 9B 1A 7C 74 B7 A9 D1 C3") {

            rawPrefix shouldBe 0x01.toUByte()
            rawDeviceModel shouldBe 0x2420.toUShort()
            rawStatus shouldBe 0x0B.toUByte()
            rawPodsBattery shouldBe 0x99.toUByte()
            rawFlags shouldBe 0x8.toUShort()
            rawCaseBattery shouldBe 0xF.toUShort()
            rawCaseLidState shouldBe 0x11.toUByte()
            rawDeviceColor shouldBe 0x0.toUByte()
            rawSuffix shouldBe 0x04.toUByte()

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

            model shouldBe PodDevice.Model.AIRPODS_PRO2_USBC
        }
    }

    @Test
    fun `AirPods Pro 2 with USB-C  - via #164 - in case`() = runTest {
        create<AirPodsPro2Usbc>("07 19 01 24 20 53 AA 98 32 00 05 49 0A B8 BF 8E 29 D8 70 12 0D A7 0C CE 77 56 00") {

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

            model shouldBe PodDevice.Model.AIRPODS_PRO2_USBC
        }
    }
}