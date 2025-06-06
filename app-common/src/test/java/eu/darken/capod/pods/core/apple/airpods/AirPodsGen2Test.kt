package eu.darken.capod.pods.core.apple.airpods

import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.BaseAirPodsTest
import eu.darken.capod.pods.core.apple.DualApplePods
import eu.darken.capod.pods.core.apple.HasAppleColor
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class AirPodsGen2Test : BaseAirPodsTest() {

    @Test
    fun `random Neighbor AirPodsGen2`() = runTest {
        create<AirPodsGen2>("07 19 01 0F 20 02 F9 8F 01 00 05 F2 7E 14 E0 54 0A 53 69 5B 7D F2 15 1F D7 B1 12") {
            pubPrefix shouldBe 0x01.toUByte()
            pubDeviceModel shouldBe 0x0F20.toUShort()
            pubStatus shouldBe 0x02.toUByte()
            pubPodsBattery shouldBe 0xF9.toUByte()
            pubFlags shouldBe 0x8.toUShort()
            pubCaseBattery shouldBe 0xF.toUShort()
            pubCaseLidState shouldBe 0x01.toUByte()
            pubDeviceColor shouldBe 0x00.toUByte()
            pubSuffix shouldBe 0x05.toUByte()

            batteryLeftPodPercent shouldBe null
            batteryRightPodPercent shouldBe 0.9f

            isCaseCharging shouldBe false
            isLeftPodCharging shouldBe false
            isRightPodCharging shouldBe false

            isLeftPodInEar shouldBe false
            isRightPodInEar shouldBe true
            batteryCasePercent shouldBe null

            caseLidState shouldBe DualApplePods.LidState.NOT_IN_CASE

            state shouldBe HasStateDetectionAirPods.ConnectionState.MUSIC

            podStyle.identifier shouldBe HasAppleColor.DeviceColor.WHITE.name

            model shouldBe PodDevice.Model.AIRPODS_GEN2
        }
    }
}