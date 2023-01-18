package eu.darken.capod.pods.core.apple.airpods

import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.BaseAirPodsTest
import eu.darken.capod.pods.core.apple.DualApplePods
import eu.darken.capod.pods.core.apple.HasAppleColor
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class AirPodsGen3Test : BaseAirPodsTest() {

    @Test
    fun `AirPods Gen3`() = runTest {
        create<AirPodsGen3>("07 19 01 13 20 75 AA B9 31 00 04 67 9A 57 DF BE F6 90 52 B0 04 1F 8D 89 DA F4 9E") {
            rawPrefix shouldBe 0x01.toUByte()
            rawDeviceModel shouldBe 0x1320.toUShort()
            rawStatus shouldBe 0x75.toUByte()
            rawPodsBattery shouldBe 0xAA.toUByte()
            rawFlags shouldBe 0xB.toUShort()
            rawCaseBattery shouldBe 0x9.toUShort()
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

            state shouldBe DualApplePods.ConnectionState.IDLE

            podStyle.identifier shouldBe HasAppleColor.DeviceColor.WHITE.name

            model shouldBe PodDevice.Model.AIRPODS_GEN3
        }
    }

    @Test
    fun `random guy at bus stop`() = runTest {
        create<AirPodsGen3>("07 19 01 13 20 2B 88 8F 01 00 08 E2 0E 84 37 C5 98 16 D4 B7 37 ED 23 8B 08 EA A1") {
            batteryLeftPodPercent shouldBe 0.8f
            batteryRightPodPercent shouldBe 0.8f

            isCaseCharging shouldBe false
            isLeftPodCharging shouldBe false
            isRightPodCharging shouldBe false

            isLeftPodInEar shouldBe true
            isRightPodInEar shouldBe true
            batteryCasePercent shouldBe null

            caseLidState shouldBe DualApplePods.LidState.NOT_IN_CASE

            state shouldBe DualApplePods.ConnectionState.UNKNOWN

            podStyle.identifier shouldBe HasAppleColor.DeviceColor.WHITE.name
        }
    }
}