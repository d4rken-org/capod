package eu.darken.capod.pods.core.apple

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Test

class BasicSingleApplePodsTest : BaseAirPodsTest() {

    @Test
    fun `test mapping`() = runBlockingTest {
        create<BasicSingleApplePods>("07 19 01 05 20 00 F5 0F 01 01 00 6D CE C0 04 22 0A 85 31 2D 82 6B 42 80 01 20 1A") {
            rawPrefix shouldBe 0x01.toUByte()
            rawDeviceModel shouldBe 0x0520.toUShort()
            rawStatus shouldBe 0x00.toUByte()
            rawPodsBattery shouldBe 0xF5.toUByte()
            rawFlags shouldBe 0x0.toUShort()
            rawCaseBattery shouldBe 0xF.toUShort()
            rawCaseLidState shouldBe 0x01.toUByte()
            rawDeviceColor shouldBe 0x01.toUByte()
            rawSuffix shouldBe 0x00.toUByte()
        }
    }

    @Test
    fun `test battery headset percent`() = runBlockingTest {
        create<BasicSingleApplePods>("07 19 01 05 20 00 F5 0F 01 01 00 6D CE C0 04 22 0A 85 31 2D 82 6B 42 80 01 20 1A") {
            batteryHeadsetPercent shouldBe 0.5f
        }
    }

}