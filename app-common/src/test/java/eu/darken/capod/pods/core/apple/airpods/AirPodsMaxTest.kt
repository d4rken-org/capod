package eu.darken.capod.pods.core.apple.airpods

import eu.darken.capod.common.isBitSet
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.BaseAirPodsTest
import eu.darken.capod.pods.core.apple.HasAppleColor
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class AirPodsMaxTest : BaseAirPodsTest() {

    // Test data from https://github.com/adolfintel/OpenPods/issues/124
    @Test
    fun `default AirPods Max`() = runTest {
        create<AirPodsMax>("07 19 01 0A 20 62 04 80 01 0F 40 0D 70 50 16 F2 40 83 16 BF 10 16 34 9B 74 84 E8") {
            rawPrefix shouldBe 0x01.toUByte()
            rawDeviceModel shouldBe 0x0A20.toUShort()
            rawStatus shouldBe 0x62.toUByte()
            rawPodsBattery shouldBe 0x04.toUByte()
            rawFlags shouldBe 0x8.toUShort()
            rawCaseBattery shouldBe 0x0.toUShort()
            rawCaseLidState shouldBe 0x01.toUByte()
            rawDeviceColor shouldBe 0x0F.toUByte()
            rawSuffix shouldBe 0x40.toUByte()

            batteryHeadsetPercent shouldBe 0.4f

            isHeadsetBeingCharged shouldBe false

            model shouldBe PodDevice.Model.AIRPODS_MAX
        }
    }

    // Test data from https://github.com/adolfintel/OpenPods/issues/124
    @Test
    fun `default AirPods Max  flipped values`() = runTest {
        create<AirPodsMax>("07 19 01 0A 20 02 05 80 04 0F 44 A7 60 9B F8 3C FD B1 D8 1C 61 EA 82 60 A3 2C 4E") {
            rawPrefix shouldBe 0x01.toUByte()
            rawDeviceModel shouldBe 0x0A20.toUShort()
            rawStatus shouldBe 0x02.toUByte()
            rawPodsBattery shouldBe 0x05.toUByte()
            rawFlags shouldBe 0x8.toUShort()
            rawCaseBattery shouldBe 0x0.toUShort()
            rawCaseLidState shouldBe 0x04.toUByte()
            rawDeviceColor shouldBe 0x0F.toUByte()
            rawSuffix shouldBe 0x44.toUByte()

            batteryHeadsetPercent shouldBe 0.5f

            isHeadsetBeingCharged shouldBe false
        }
    }

    @Test
    fun `some dude at the gym`() = runTest {
        create<AirPodsMax>("07 19 01 0A 20 23 07 80 03 03 65 1F 28 32 D0 D9 71 43 00 9A 40 E7 6B EA 6C 2C FB") {
            rawPrefix shouldBe 0x01.toUByte()
            rawDeviceModel shouldBe 0x0A20.toUShort()
            rawStatus shouldBe 0x23.toUByte()
            rawPodsBattery shouldBe 0x07.toUByte()
            rawFlags shouldBe 0x8.toUShort()
            rawCaseBattery shouldBe 0x0.toUShort()
            rawCaseLidState shouldBe 0x03.toUByte()
            rawDeviceColor shouldBe 0x03.toUByte()
            rawSuffix shouldBe 0x65.toUByte()

            batteryHeadsetPercent shouldBe 0.7f

            isHeadsetBeingCharged shouldBe false

            rawStatus.isBitSet(5) shouldBe true

            podStyle shouldBe HasAppleColor.DeviceColor.BLUE
        }
    }

    @Test
    fun `wear status`() = runTest {
        create<AirPodsMax>("07 19 01 0A 20 03 07 80 03 03 65 1F 28 32 D0 D9 71 43 00 9A 40 E7 6B EA 6C 2C FB") {
            rawStatus shouldBe 0x03.toUByte()

            rawStatus.isBitSet(5) shouldBe false
            isBeingWorn shouldBe false
        }
        create<AirPodsMax>("07 19 01 0A 20 23 07 80 03 03 65 1F 28 32 D0 D9 71 43 00 9A 40 E7 6B EA 6C 2C FB") {
            rawStatus shouldBe 0x23.toUByte()

            rawStatus.isBitSet(5) shouldBe true
            isBeingWorn shouldBe true
        }
    }
}