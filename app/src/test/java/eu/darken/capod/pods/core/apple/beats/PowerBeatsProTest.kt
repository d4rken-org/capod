package eu.darken.capod.pods.core.apple.beats

import eu.darken.capod.pods.core.apple.BaseAirPodsTest
import eu.darken.capod.pods.core.apple.HasAppleColor
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Test

class PowerBeatsProTest : BaseAirPodsTest() {

    // TODO This is handcrafted data, get actual data for tests
    @Test
    fun `test PowerBeatsPro`() = runBlockingTest {
        create<PowerBeatsPro>("07 19 01 0B 20 54 AA B5 31 00 00 E0 0C A7 8A 60 4B D3 7D F4 60 4F 2C 73 E9 A7 F4") {
            rawPrefix shouldBe 0x01.toUByte()
            rawDeviceModel shouldBe 0x0B20.toUShort()
            rawStatus shouldBe 0x54.toUByte()
            rawPodsBattery shouldBe 0xAA.toUByte()
            rawFlags shouldBe 0xB.toUShort()
            rawCaseBattery shouldBe 0x5.toUShort()
            rawCaseLidState shouldBe 0x31.toUByte()
            rawDeviceColor shouldBe 0x00.toUByte()
            rawSuffix shouldBe 0x00.toUByte()

            isLeftPodMicrophone shouldBe true
            isRightPodMicrophone shouldBe false

            batteryLeftPodPercent shouldBe 1.0f
            batteryRightPodPercent shouldBe 1.0f

            isCaseCharging shouldBe false
            isRightPodCharging shouldBe true
            isLeftPodCharging shouldBe true
            batteryCasePercent shouldBe 0.5f

            podStyle.identifier shouldBe HasAppleColor.DeviceColor.WHITE.name
        }
    }
}