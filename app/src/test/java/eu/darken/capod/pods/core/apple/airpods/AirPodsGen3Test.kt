package eu.darken.capod.pods.core.apple.airpods

import eu.darken.capod.pods.core.apple.BaseAirPodsTest
import eu.darken.capod.pods.core.apple.DualApplePods
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Test

class AirPodsGen3Test : BaseAirPodsTest() {

    @Test
    fun `AirPods Gen3`() = runBlockingTest {
        create<AirPodsGen3>("07 19 01 13 20 75 AA B9 31 00 04 67 9A 57 DF BE F6 90 52 B0 04 1F 8D 89 DA F4 9E") {
            rawPrefix shouldBe 0x01.toUByte()
            rawDeviceModel shouldBe 0x1320.toUShort()
            rawStatus shouldBe 0x75.toUByte()
            rawPodsBattery shouldBe 0xAA.toUByte()
            rawCaseBattery shouldBe 0xB9.toUByte()
            rawCaseLidState shouldBe 0x31.toUByte()
            rawDeviceColor shouldBe 0x00.toUByte()
            rawSuffix shouldBe 0x04.toUByte()

            batteryLeftPodPercent shouldBe 1.0f
            batteryRightPodPercent shouldBe 1.0f

            isCaseCharging shouldBe false
            isLeftPodCharging shouldBe true
            isRightPodCharging shouldBe true

            isLeftPodInEar shouldBe false
            isRightPodInEar shouldBe false
            batteryCasePercent shouldBe 0.9f

            caseLidState shouldBe DualApplePods.LidState.OPEN

            connectionState shouldBe DualApplePods.ConnectionState.IDLE

            deviceColor shouldBe DualApplePods.DeviceColor.WHITE
        }
    }

    @Test
    fun `random guy at bus stop`() = runBlockingTest {
        create<AirPodsGen3>("07 19 01 13 20 2B 88 8F 01 00 08 E2 0E 84 37 C5 98 16 D4 B7 37 ED 23 8B 08 EA A1") {
            batteryLeftPodPercent shouldBe null
            batteryRightPodPercent shouldBe 0.9f

            isCaseCharging shouldBe false
            isLeftPodCharging shouldBe false
            isRightPodCharging shouldBe false

            isLeftPodInEar shouldBe false
            isRightPodInEar shouldBe true
            batteryCasePercent shouldBe null

            caseLidState shouldBe DualApplePods.LidState.NOT_IN_CASE

            connectionState shouldBe DualApplePods.ConnectionState.MUSIC

            deviceColor shouldBe DualApplePods.DeviceColor.WHITE
        }
    }
}