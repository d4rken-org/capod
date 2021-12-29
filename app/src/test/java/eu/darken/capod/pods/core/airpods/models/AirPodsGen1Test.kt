package eu.darken.capod.pods.core.airpods.models

import eu.darken.capod.pods.core.airpods.AirPodsDevice
import eu.darken.capod.pods.core.airpods.BaseAirPodsTest
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Test

class AirPodsGen1Test : BaseAirPodsTest() {

    // Test data from https://github.com/adolfintel/OpenPods/issues/39#issuecomment-557664269
    @Test
    fun `fake airpods`() = runBlockingTest {
        create<AirPodsGen1>("07 19 01 02 20 55 AF 56 31 00 00 6F E4 DF 10 AF 10 60 81 03 3B 76 D9 C7 11 22 88") {
            rawPrefix shouldBe 0x01.toUByte()
            rawDeviceModel shouldBe 0x0220.toUShort()
            rawStatus shouldBe 0x55.toUByte()
            rawPodsBattery shouldBe 0xAF.toUByte()
            rawCaseBattery shouldBe 0x56.toUByte()
            rawCaseLidState shouldBe 0x31.toUByte()
            rawDeviceColor shouldBe 0x00.toUByte()
            rawSuffix shouldBe 0x00.toUByte()

            batteryLeftPodPercent shouldBe 1.0f
            batteryRightPodPercent shouldBe null

            isCaseCharging shouldBe true
            isLeftPodCharging shouldBe false
            isRightPodCharging shouldBe true

            isLeftPodInEar shouldBe false
            isRightPodInEar shouldBe false
            batteryCasePercent shouldBe 0.6f

            caseLidState shouldBe AirPodsDevice.LidState.OPEN

            connectionState shouldBe AirPodsDevice.ConnectionState.DISCONNECTED

            deviceColor shouldBe AirPodsDevice.DeviceColor.WHITE
        }
    }
}