package eu.darken.capod.pods.core.apple

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class SingleApplePodsTest : BaseAirPodsTest() {

    @Test
    fun `default bit mapping Max`() = runTest {
        create<SingleApplePods>("07 19 01 0A 20 62 04 80 01 0F 40 0D 70 50 16 F2 40 83 16 BF 10 16 34 9B 74 84 E8") {
            pubPrefix shouldBe 0x01.toUByte()
            pubDeviceModel shouldBe 0x0A20.toUShort()
            pubStatus shouldBe 0x62.toUByte()
            pubPodsBattery shouldBe 0x04.toUByte()
            pubFlags shouldBe 0x8.toUShort()
            pubCaseBattery shouldBe 0x0.toUShort()
            pubCaseLidState shouldBe 0x01.toUByte()
            pubDeviceColor shouldBe 0x0F.toUByte()
            pubSuffix shouldBe 0x40.toUByte()
        }
    }
}