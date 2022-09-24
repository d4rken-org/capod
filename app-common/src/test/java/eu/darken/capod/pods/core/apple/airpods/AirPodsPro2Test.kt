package eu.darken.capod.pods.core.apple.airpods

import eu.darken.capod.pods.core.apple.BaseAirPodsTest
import eu.darken.capod.pods.core.apple.HasAppleColor
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Test

class AirPodsPro2Test : BaseAirPodsTest() {

    /**
     * https://github.com/d4rken-org/capod/issues/31#issuecomment-1256791084
     */
    @Test
    fun `test AirPods Pro 2 - unknown setup from #31`() = runBlockingTest {
        create<AirPodsPro2>("07 19 01 14 20 55 88 F9 51 00 04 20 50 03 CA D5 C9 AC 0F FA 84 78 94 5A 4D DF F5") {

            rawPrefix shouldBe 0x01.toUByte()
            rawDeviceModel shouldBe 0x1420.toUShort()
            rawStatus shouldBe 0x55.toUByte()
            rawPodsBattery shouldBe 0x88.toUByte()
            rawFlags shouldBe 0xF.toUShort()
            rawCaseBattery shouldBe 0x9.toUShort()
            rawCaseLidState shouldBe 0x51.toUByte()
            rawDeviceColor shouldBe 0x0.toUByte()
            rawSuffix shouldBe 0x04.toUByte()

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
        }
    }
}