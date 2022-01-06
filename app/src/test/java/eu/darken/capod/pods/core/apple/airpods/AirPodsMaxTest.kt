package eu.darken.capod.pods.core.apple.airpods

import eu.darken.capod.pods.core.apple.BaseAirPodsTest
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Test

class AirPodsMaxTest : BaseAirPodsTest() {

    // Test data from https://github.com/adolfintel/OpenPods/issues/124
    @Test
    fun `default AirPods Max`() = runBlockingTest {
        create<AirPodsMax>("07 19 01 0A 20 62 04 80 01 0F 40 0D 70 50 16 F2 40 83 16 BF 10 16 34 9B 74 84 E8") {
            rawPrefix shouldBe 0x01.toUByte()
            rawDeviceModel shouldBe 0x0A20.toUShort()
            rawStatus shouldBe 0x62.toUByte()
            rawPodsBattery shouldBe 0x04.toUByte()
            rawCaseBattery shouldBe 0x80.toUByte()
            rawCaseLidState shouldBe 0x01.toUByte()
            rawDeviceColor shouldBe 0x0F.toUByte()
            rawSuffix shouldBe 0x40.toUByte()

            batteryHeadsetPercent shouldBe 0.4f

            isHeadsetBeingCharged shouldBe false

            isHeadphonesBeingWorn shouldBe true
        }
    }

    // Test data from https://github.com/adolfintel/OpenPods/issues/124
    @Test
    fun `default AirPods Max  flipped values`() = runBlockingTest {
        create<AirPodsMax>("07 19 01 0A 20 02 05 80 04 0F 44 A7 60 9B F8 3C FD B1 D8 1C 61 EA 82 60 A3 2C 4E") {
            rawPrefix shouldBe 0x01.toUByte()
            rawDeviceModel shouldBe 0x0A20.toUShort()
            rawStatus shouldBe 0x02.toUByte()
            rawPodsBattery shouldBe 0x05.toUByte()
            rawCaseBattery shouldBe 0x80.toUByte()
            rawCaseLidState shouldBe 0x04.toUByte()
            rawDeviceColor shouldBe 0x0F.toUByte()
            rawSuffix shouldBe 0x44.toUByte()

            batteryHeadsetPercent shouldBe 0.5f

            isHeadsetBeingCharged shouldBe false

            isHeadphonesBeingWorn shouldBe true
        }
    }
}