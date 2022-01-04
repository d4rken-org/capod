package eu.darken.capod.pods.core.apple.beats

import eu.darken.capod.pods.core.apple.BaseAirPodsTest
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Test

class PowerBeats3Test : BaseAirPodsTest() {

    // TODO This is handcrafted data, get actual data for tests
    @Test
    fun `default PowerBeats3`() = runBlockingTest {
        create<PowerBeats3>("07 19 01 13 20 62 04 80 01 0F 40 0D 70 50 16 F2 40 83 16 BF 10 16 34 9B 74 84 E8") {
            rawPrefix shouldBe 0x01.toUByte()
            rawDeviceModel shouldBe 0x1320.toUShort()
            rawStatus shouldBe 0x62.toUByte()
            rawPodsBattery shouldBe 0x04.toUByte()
            rawCaseBattery shouldBe 0x80.toUByte()
            rawCaseLidState shouldBe 0x01.toUByte()
            rawDeviceColor shouldBe 0x0F.toUByte()
            rawSuffix shouldBe 0x40.toUByte()

            batteryHeadsetPercent shouldBe 0.4f
        }
    }

    // PowerBeats3 unconfirmed
    @Test
    fun `random guy at busstop`() = runBlockingTest {
        create<PowerBeats3>("07 19 01 13 20 2B 88 8F 01 00 08 E2 0E 84 37 C5 98 16 D4 B7 37 ED 23 8B 08 EA A1") {
            batteryHeadsetPercent shouldBe 0.8f
        }
    }
}