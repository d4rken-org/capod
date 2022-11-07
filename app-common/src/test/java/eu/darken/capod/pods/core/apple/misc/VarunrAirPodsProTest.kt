package eu.darken.capod.pods.core.apple.misc

import eu.darken.capod.pods.core.apple.BaseAirPodsTest
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class VarunrAirPodsProTest : BaseAirPodsTest() {

    @Test
    fun `guessed data`() = runTest {
        create<VarunrAirPodsPro>("07 13 01 0E 20 71 AA 37 36 00 10 00 FF 64 FF 00 00 00 00 00 00") {
            rawPrefix shouldBe 0x01.toUByte()
            rawDeviceModel shouldBe 0x0E20.toUShort()
            rawStatus shouldBe 0x71.toUByte()
            rawPodsBattery shouldBe 0xAA.toUByte()
            rawFlags shouldBe 0x3.toUShort()
            rawCaseBattery shouldBe 0x7.toUShort()
            rawCaseLidState shouldBe 0x36.toUByte()
            rawDeviceColor shouldBe 0x00.toUByte()
            rawSuffix shouldBe 0x10.toUByte()

            batteryLeftPodPercent shouldBe 1.0f
            batteryRightPodPercent shouldBe 1.0f

            isCaseCharging shouldBe false

            batteryCasePercent shouldBe 0.7f
        }
    }
}