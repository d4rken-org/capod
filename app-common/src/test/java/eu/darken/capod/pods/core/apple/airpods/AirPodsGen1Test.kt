package eu.darken.capod.pods.core.apple.airpods

import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.BaseAirPodsTest
import eu.darken.capod.pods.core.apple.DualApplePods
import eu.darken.capod.pods.core.apple.HasAppleColor
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class AirPodsGen1Test : BaseAirPodsTest() {

    // Test data from https://github.com/adolfintel/OpenPods/issues/39#issuecomment-557664269
    @Test
    fun `fake airpods`() = runTest {
        create<AirPodsGen1>("07 19 01 02 20 55 AF 56 31 00 00 6F E4 DF 10 AF 10 60 81 03 3B 76 D9 C7 11 22 88") {
            pubPrefix shouldBe 0x01.toUByte()
            pubDeviceModel shouldBe 0x0220.toUShort()
            pubStatus shouldBe 0x55.toUByte()
            pubPodsBattery shouldBe 0xAF.toUByte()
            pubFlags shouldBe 0x5.toUShort()
            pubCaseBattery shouldBe 0x6.toUShort()
            pubCaseLidState shouldBe 0x31.toUByte()
            pubDeviceColor shouldBe 0x00.toUByte()
            pubSuffix shouldBe 0x00.toUByte()

            batteryLeftPodPercent shouldBe 1.0f
            batteryRightPodPercent shouldBe null

            isCaseCharging shouldBe true
            isLeftPodCharging shouldBe false
            isRightPodCharging shouldBe true

            isLeftPodInEar shouldBe false
            isRightPodInEar shouldBe false
            batteryCasePercent shouldBe 0.6f

            caseLidState shouldBe DualApplePods.LidState.OPEN

            podStyle.identifier shouldBe HasAppleColor.DeviceColor.WHITE.name

            model shouldBe PodDevice.Model.AIRPODS_GEN1
        }
    }
}