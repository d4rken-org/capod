package eu.darken.capod.pods.core.apple.airpods

import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.BaseAirPodsTest
import eu.darken.capod.pods.core.apple.DualApplePods
import eu.darken.capod.pods.core.apple.HasAppleColor
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class AirPodsGen4AncTest : BaseAirPodsTest() {

    @Test
    fun `AirPods Gen4 with ANC via log from #226`() = runTest {
        create<AirPodsGen4Anc>("07 19 01 1B 20 0B 9A 8F 10 00 04 43 DF EC 1D D3 F1 C3 F4 A1 9B 29 26 B9 E7 3A A0") {
            pubPrefix shouldBe 0x01.toUByte()
            pubDeviceModel shouldBe 0x1B20.toUShort()
            pubStatus shouldBe 0x0b.toUByte()
            pubPodsBattery shouldBe 0x9A.toUByte()
            pubFlags shouldBe 0x8.toUShort()
            pubCaseBattery shouldBe 0xF.toUShort()
            pubCaseLidState shouldBe 0x10.toUByte()
            pubDeviceColor shouldBe 0x00.toUByte()
            pubSuffix shouldBe 0x04.toUByte()

            batteryLeftPodPercent shouldBe 0.9f
            batteryRightPodPercent shouldBe 1.0f

            isCaseCharging shouldBe false
            isLeftPodCharging shouldBe false
            isRightPodCharging shouldBe false

            isLeftPodInEar shouldBe true
            isRightPodInEar shouldBe true
            batteryCasePercent shouldBe null

            caseLidState shouldBe DualApplePods.LidState.NOT_IN_CASE

            state shouldBe HasStateDetectionAirPods.ConnectionState.IDLE

            podStyle.identifier shouldBe HasAppleColor.DeviceColor.WHITE.name

            model shouldBe PodDevice.Model.AIRPODS_GEN4_ANC
        }
    }
}