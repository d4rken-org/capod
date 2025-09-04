package eu.darken.capod.pods.core.apple.beats

import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.BaseAirPodsTest
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class PowerBeats3Test : BaseAirPodsTest() {

    // TODO This is handcrafted data, get actual data for tests
    @Test
    fun `default PowerBeats3`() = runTest {
        create<PowerBeats3>("07 19 01 03 20 62 04 80 01 0F 40 0D 70 50 16 F2 40 83 16 BF 10 16 34 9B 74 84 E8") {
            pubPrefix shouldBe 0x01.toUByte()
            pubDeviceModel shouldBe 0x0320.toUShort()
            pubStatus shouldBe 0x62.toUByte()
            pubPodsBattery shouldBe 0x04.toUByte()
            pubFlags shouldBe 0x8.toUShort()
            pubCaseBattery shouldBe 0x0.toUShort()
            pubCaseLidState shouldBe 0x01.toUByte()
            pubDeviceColor shouldBe 0x0F.toUByte()
            pubSuffix shouldBe 0x40.toUByte()

            batteryHeadsetPercent shouldBe 0.4f

            model shouldBe PodDevice.Model.POWERBEATS_3
        }
    }
}