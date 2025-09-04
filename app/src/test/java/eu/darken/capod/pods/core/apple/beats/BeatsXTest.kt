package eu.darken.capod.pods.core.apple.beats

import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.BaseAirPodsTest
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class BeatsXTest : BaseAirPodsTest() {

    // Raw data from https://github.com/adolfintel/OpenPods/issues/105
    @Test
    fun `default BeatsX`() = runTest {
        create<BeatsX>("07 19 01 05 20 00 F5 0F 01 01 00 6D CE C0 04 22 0A 85 31 2D 82 6B 42 80 01 20 1A") {
            pubPrefix shouldBe 0x01.toUByte()
            pubDeviceModel shouldBe 0x0520.toUShort()
            pubStatus shouldBe 0x00.toUByte()
            pubPodsBattery shouldBe 0xF5.toUByte()
            pubFlags shouldBe 0x0.toUShort()
            pubCaseBattery shouldBe 0xF.toUShort()
            pubCaseLidState shouldBe 0x01.toUByte()
            pubDeviceColor shouldBe 0x01.toUByte()
            pubSuffix shouldBe 0x00.toUByte()

            batteryHeadsetPercent shouldBe 0.5f

            model shouldBe PodDevice.Model.BEATS_X
        }
    }
}