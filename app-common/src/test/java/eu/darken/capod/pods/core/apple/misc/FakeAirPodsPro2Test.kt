package eu.darken.capod.pods.core.apple.misc

import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.BaseAirPodsTest
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class FakeAirPodsPro2Test : BaseAirPodsTest() {

    @Test
    fun `guessed data`() = runTest {
        create<FakeAirPodsPro2>("07 13 01 14 20 75 AA 58 35 00 10 00 E4 E4 26 00 00 00 00 00 00") {
            rawPrefix shouldBe 0x01.toUByte()
            rawDeviceModel shouldBe 0x1420.toUShort()
            rawStatus shouldBe 0x75.toUByte()
            rawPodsBattery shouldBe 0xAA.toUByte()
            rawFlags shouldBe 0x5.toUShort()
            rawCaseBattery shouldBe 0x8.toUShort()
            rawCaseLidState shouldBe 0x35.toUByte()
            rawDeviceColor shouldBe 0x00.toUByte()
            rawSuffix shouldBe 0x10.toUByte()

            batteryLeftPodPercent shouldBe 1.0f
            batteryRightPodPercent shouldBe 1.0f

            isCaseCharging shouldBe true

            batteryCasePercent shouldBe 0.8f

            model shouldBe PodDevice.Model.FAKE_AIRPODS_PRO2
        }
    }
}