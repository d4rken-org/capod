package eu.darken.capod.pods.core.apple.misc

import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.BaseAirPodsTest
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class FakeAirPodsGen3Test : BaseAirPodsTest() {

    @Test
    fun `charging in case`() = runTest {
        create<FakeAirPodsGen3>("07 13 01 13 20 75 AA 37 34 00 10 00 E4 E4 64 00 00 00 00 00 00") {
            pubPrefix shouldBe 0x01.toUByte()
            pubDeviceModel shouldBe 0x1320.toUShort()
            pubStatus shouldBe 0x75.toUByte()
            pubPodsBattery shouldBe 0xAA.toUByte()
            pubFlags shouldBe 0x3.toUShort()
            pubCaseBattery shouldBe 0x7.toUShort()
            pubCaseLidState shouldBe 0x34.toUByte()
            pubDeviceColor shouldBe 0x00.toUByte()
            pubSuffix shouldBe 0x10.toUByte()

            batteryLeftPodPercent shouldBe 1f
            batteryRightPodPercent shouldBe 1f

            isLeftPodInEar shouldBe false
            isRightPodInEar shouldBe false

            isCaseCharging shouldBe false
            isLeftPodCharging shouldBe true
            isRightPodCharging shouldBe true

            batteryCasePercent shouldBe 0.7f

            model shouldBe PodDevice.Model.FAKE_AIRPODS_GEN3
        }
    }
}