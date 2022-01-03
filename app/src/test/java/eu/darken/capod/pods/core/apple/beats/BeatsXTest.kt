package eu.darken.capod.pods.core.apple.beats

import eu.darken.capod.pods.core.apple.BaseAirPodsTest
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Test

class BeatsXTest : BaseAirPodsTest() {

    // Raw data from https://github.com/adolfintel/OpenPods/issues/105
    @Test
    fun `default BeatsX`() = runBlockingTest {
        create<BeatsX>("07 19 01 05 20 00 F5 0F 01 01 00 6D CE C0 04 22 0A 85 31 2D 82 6B 42 80 01 20 1A") {
            rawPrefix shouldBe 0x01.toUByte()
            rawDeviceModel shouldBe 0x0520.toUShort()
            rawStatus shouldBe 0x00.toUByte()
            rawPodsBattery shouldBe 0xF5.toUByte()
            rawCaseBattery shouldBe 0x0F.toUByte()
            rawCaseLidState shouldBe 0x01.toUByte()
            rawDeviceColor shouldBe 0x01.toUByte()
            rawSuffix shouldBe 0x00.toUByte()

            batteryHeadsetPercent shouldBe 0.5f
        }
    }
}