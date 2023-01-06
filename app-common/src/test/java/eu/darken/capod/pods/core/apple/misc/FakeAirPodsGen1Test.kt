package eu.darken.capod.pods.core.apple.misc

import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.BaseAirPodsTest
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class FakeAirPodsGen1Test : BaseAirPodsTest() {

    @Test
    fun `charging in box`() = runTest {
        create<FakeAirPodsGen1>("07 13 01 02 20 71 AA 37 32 00 10 00 64 64 FF 00 00 00 00 00 00") {
            rawPrefix shouldBe 0x01.toUByte()
            rawDeviceModel shouldBe 0x0220.toUShort()
            rawStatus shouldBe 0x71.toUByte()
            rawPodsBattery shouldBe 0xAA.toUByte()
            rawFlags shouldBe 0x3.toUShort()
            rawCaseBattery shouldBe 0x7.toUShort()
            rawCaseLidState shouldBe 0x32.toUByte()
            rawDeviceColor shouldBe 0x00.toUByte()
            rawSuffix shouldBe 0x10.toUByte()

            batteryLeftPodPercent shouldBe 1.0f
            batteryRightPodPercent shouldBe 1.0f

            isLeftPodInEar shouldBe false
            isRightPodInEar shouldBe false

            isLeftPodCharging shouldBe true
            isRightPodCharging shouldBe true

            isCaseCharging shouldBe false

            batteryCasePercent shouldBe 0.7f

            model shouldBe PodDevice.Model.FAKE_AIRPODS_GEN1
        }
    }
}