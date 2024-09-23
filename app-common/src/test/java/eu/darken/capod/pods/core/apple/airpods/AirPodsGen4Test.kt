package eu.darken.capod.pods.core.apple.airpods

import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.BaseAirPodsTest
import eu.darken.capod.pods.core.apple.DualApplePods
import eu.darken.capod.pods.core.apple.HasAppleColor
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class AirPodsGen4Test : BaseAirPodsTest() {

    @Test
    fun `AirPods Gen4 via log from #225`() = runTest {
        create<AirPodsGen4>("07 19 01 19 20 2B 33 8F 11 00 04 59 D4 57 20 0F 1C 13 38 B2 00 74 E9 DD 70 D7 A5") {
            rawPrefix shouldBe 0x01.toUByte()
            rawDeviceModel shouldBe 0x1920.toUShort()
            rawStatus shouldBe 0x2B.toUByte()
            rawPodsBattery shouldBe 0x33.toUByte()
            rawFlags shouldBe 0x8.toUShort()
            rawCaseBattery shouldBe 0xF.toUShort()
            rawCaseLidState shouldBe 0x11.toUByte()
            rawDeviceColor shouldBe 0x00.toUByte()
            rawSuffix shouldBe 0x04.toUByte()

            batteryLeftPodPercent shouldBe 0.3f
            batteryRightPodPercent shouldBe 0.3f

            isCaseCharging shouldBe false
            isLeftPodCharging shouldBe false
            isRightPodCharging shouldBe false

            isLeftPodInEar shouldBe true
            isRightPodInEar shouldBe true
            batteryCasePercent shouldBe null

            caseLidState shouldBe DualApplePods.LidState.UNKNOWN

            state shouldBe HasStateDetectionAirPods.ConnectionState.IDLE

            podStyle.identifier shouldBe HasAppleColor.DeviceColor.WHITE.name

            model shouldBe PodDevice.Model.AIRPODS_GEN4
        }
    }
}