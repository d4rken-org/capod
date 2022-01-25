package eu.darken.capod.pods.core.apple

import eu.darken.capod.pods.core.apple.airpods.AirPodsMax
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Test

class SingleApplePodsTest : BaseAirPodsTest() {

    @Test
    fun `default bit mapping Max`() = runBlockingTest {
        create<SingleApplePods>("07 19 01 0A 20 62 04 80 01 0F 40 0D 70 50 16 F2 40 83 16 BF 10 16 34 9B 74 84 E8") {
            rawPrefix shouldBe 0x01.toUByte()
            rawDeviceModel shouldBe 0x0A20.toUShort()
            rawStatus shouldBe 0x62.toUByte()
            rawPodsBattery shouldBe 0x04.toUByte()
            rawFlags shouldBe 0x8.toUShort()
            rawCaseBattery shouldBe 0x0.toUShort()
            rawCaseLidState shouldBe 0x01.toUByte()
            rawDeviceColor shouldBe 0x0F.toUByte()
            rawSuffix shouldBe 0x40.toUByte()
        }
    }

    @Test
    fun `test values based on AirPodMax`() = runBlockingTest {
        create<AirPodsMax>("07 19 01 0A 20 02 05 80 04 0F 44 A7 60 9B F8 3C FD B1 D8 1C 61 EA 82 60 A3 2C 4E") {
            batteryHeadsetPercent shouldBe 0.5f

            isHeadsetBeingCharged shouldBe false

            isHeadphonesBeingWorn shouldBe true
        }
    }
}