package eu.darken.capod.pods.core.apple.fakes

import eu.darken.capod.pods.core.apple.BaseAirPodsTest
import eu.darken.capod.pods.core.apple.DualApplePods
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Test

class Twsi99999Test : BaseAirPodsTest() {

    @Test
    fun `charging in box`() = runBlockingTest {
        create<Twsi99999>("07 13 01 02 20 71 AA 37 32 00 10 00 64 64 FF 00 00 00 00 00 00") {
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

            isCaseCharging shouldBe false
            isLeftPodCharging shouldBe true
            isRightPodCharging shouldBe true

            isLeftPodInEar shouldBe false
            isRightPodInEar shouldBe false
            batteryCasePercent shouldBe 0.7f

            caseLidState shouldBe DualApplePods.LidState.OPEN

            connectionState shouldBe DualApplePods.ConnectionState.UNKNOWN

            deviceColor shouldBe DualApplePods.DeviceColor.WHITE
        }
    }
}