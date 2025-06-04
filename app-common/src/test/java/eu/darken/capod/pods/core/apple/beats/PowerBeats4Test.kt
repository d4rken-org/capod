package eu.darken.capod.pods.core.apple.beats

import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.BaseAirPodsTest
import eu.darken.capod.pods.core.apple.airpods.HasStateDetectionAirPods
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class PowerBeats4Test : BaseAirPodsTest() {

    @Test
    fun `playing music`() = runTest {
        create<PowerBeats4>("07 19 01 0D 20 00 02 80 01 00 05 07 C5 E1 9C BD 9A 05 0E AE 9E 56 53 2F F9 75 4A") {
            pubPrefix shouldBe 0x01.toUByte()
            pubDeviceModel shouldBe 0x0D20.toUShort()
            pubStatus shouldBe 0x0.toUByte()
            pubPodsBattery shouldBe 0x02.toUByte()
            pubFlags shouldBe 0x8.toUShort()
            pubCaseBattery shouldBe 0x0.toUShort()
            pubCaseLidState shouldBe 0x01.toUByte()
            pubDeviceColor shouldBe 0x00.toUByte()
            pubSuffix shouldBe 0x05.toUByte()

            batteryHeadsetPercent shouldBe 0.2f

            model shouldBe PodDevice.Model.POWERBEATS_4

            state shouldBe HasStateDetectionAirPods.ConnectionState.MUSIC
        }
    }

    @Test
    fun `extra test case`() = runTest {
        create<PowerBeats4>("07 19 01 0D 20 00 02 80 02 00 04 80 90 60 77 32 C2 0C 75 7A 3D 7E D7 1C 0E B8 43") {
            pubPrefix shouldBe 0x01.toUByte()
            pubDeviceModel shouldBe 0x0D20.toUShort()
            pubStatus shouldBe 0x0.toUByte()
            pubPodsBattery shouldBe 0x02.toUByte()
            pubFlags shouldBe 0x8.toUShort()
            pubCaseBattery shouldBe 0x0.toUShort()
            pubCaseLidState shouldBe 0x02.toUByte()
            pubDeviceColor shouldBe 0x00.toUByte()
            pubSuffix shouldBe 0x04.toUByte()

            batteryHeadsetPercent shouldBe 0.2f

            model shouldBe PodDevice.Model.POWERBEATS_4

            state shouldBe HasStateDetectionAirPods.ConnectionState.IDLE
        }
    }


    @Test
    fun `disconnected state`() = runTest {
        create<PowerBeats4>("07 19 01 0D 20 00 02 80 01 00 00 25 DF 66 40 AF 44 7B 77 95 8F D1 92 50 26 11 74") {
            pubPrefix shouldBe 0x01.toUByte()
            pubDeviceModel shouldBe 0x0D20.toUShort()
            pubStatus shouldBe 0x0.toUByte()
            pubPodsBattery shouldBe 0x02.toUByte()
            pubFlags shouldBe 0x8.toUShort()
            pubCaseBattery shouldBe 0x0.toUShort()
            pubCaseLidState shouldBe 0x01.toUByte()
            pubDeviceColor shouldBe 0x00.toUByte()
            pubSuffix shouldBe 0x00.toUByte()

            batteryHeadsetPercent shouldBe 0.2f

            model shouldBe PodDevice.Model.POWERBEATS_4

            state shouldBe HasStateDetectionAirPods.ConnectionState.DISCONNECTED
        }
    }
}