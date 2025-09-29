package eu.darken.capod.pods.core.apple.airpods

import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.BaseAirPodsTest
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class AirPodsMaxUsbcTest : BaseAirPodsTest() {

    // Test data from https://github.com/d4rken-org/capod/issues/236
    @Test
    fun `default AirPods Max`() = runTest {
        create<AirPodsMaxUsbc>("07 19 01 1F 20 2B 05 80 03 12 C5 2E 8B F9 9A 7E 19 7B 63 0F 30 6E D7 3B E2 EC 32") {
            pubPrefix shouldBe 0x01.toUByte()
            pubDeviceModel shouldBe 0x1F20.toUShort()
            pubStatus shouldBe 0x2B.toUByte()
            pubPodsBattery shouldBe 0x05.toUByte()
            pubFlags shouldBe 0x8.toUShort()
            pubCaseBattery shouldBe 0x0.toUShort()
            pubCaseLidState shouldBe 0x03.toUByte()
            pubDeviceColor shouldBe 0x12.toUByte()
            pubSuffix shouldBe 0xC5.toUByte()

            batteryHeadsetPercent shouldBe 0.5f

            isHeadsetBeingCharged shouldBe false

            model shouldBe PodDevice.Model.AIRPODS_MAX_USBC
        }
    }
}