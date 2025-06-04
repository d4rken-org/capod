package eu.darken.capod.pods.core.apple.beats

import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.BaseAirPodsTest
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class BeatsFlexText : BaseAirPodsTest() {

    // Raw data from https://github.com/adolfintel/OpenPods/issues/105
    @Test
    fun `default BeatsFlex`() = runTest {
        create<BeatsFlex>("07 19 01 10 20 0A F4 8F 00 01 00 C4 71 9F 9C EF A2 E3 BA 66 FE 1D 45 9F C9 2F A0") {
            pubPrefix shouldBe 0x01.toUByte()
            pubDeviceModel shouldBe 0x1020.toUShort()
            pubStatus shouldBe 0x0A.toUByte()
            pubPodsBattery shouldBe 0xF4.toUByte()
            pubFlags shouldBe 0x8.toUShort()
            pubCaseBattery shouldBe 0xF.toUShort()
            pubCaseLidState shouldBe 0x00.toUByte()
            pubDeviceColor shouldBe 0x01.toUByte()
            pubSuffix shouldBe 0x00.toUByte()

            batteryHeadsetPercent shouldBe 0.4f

            model shouldBe PodDevice.Model.BEATS_FLEX
        }
    }

    @Test
    fun `random neighbour`() = runTest {
        create<BeatsFlex>("07 19 01 10 20 0A F6 8F 02 4F 00 95 68 94 9E 99 D6 90 F4 5E 68 3C 58 21 68 9F 0D") {

            batteryHeadsetPercent shouldBe 0.6f
        }
    }

}