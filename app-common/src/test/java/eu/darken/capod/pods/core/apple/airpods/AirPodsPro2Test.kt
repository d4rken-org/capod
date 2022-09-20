package eu.darken.capod.pods.core.apple.airpods

import eu.darken.capod.pods.core.apple.BaseAirPodsTest
import eu.darken.capod.pods.core.apple.HasAppleColor
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Test

class AirPodsPro2Test : BaseAirPodsTest() {

    @Test
    fun `test AirPods Pro 2 - unknown setup from #31`() = runBlockingTest {
        create<AirPodsPro2>("07 19 01 11 20 2B 88 0F 00 11 05 77 7B 0E E5 B8 AD 03 57 2F FA AA 2C F5 65 0F 52") {

            rawPrefix shouldBe 0x01.toUByte()
            rawDeviceModel shouldBe 0x1120.toUShort()
            rawStatus shouldBe 0x2B.toUByte()
            rawPodsBattery shouldBe 0x88.toUByte()
            rawFlags shouldBe 0x0.toUShort()
            rawCaseBattery shouldBe 0xF.toUShort()
            rawCaseLidState shouldBe 0x00.toUByte()
            rawDeviceColor shouldBe 0x11.toUByte()
            rawSuffix shouldBe 0x05.toUByte()

            isLeftPodMicrophone shouldBe true
            isRightPodMicrophone shouldBe false

            batteryLeftPodPercent shouldBe 0.8f
            batteryRightPodPercent shouldBe 0.8f

            isCaseCharging shouldBe false
            isRightPodCharging shouldBe false
            isLeftPodCharging shouldBe false
            batteryCasePercent shouldBe null

            podStyle.identifier shouldBe HasAppleColor.DeviceColor.UNKNOWN.name
        }
    }
}