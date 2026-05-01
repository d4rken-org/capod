package eu.darken.capod.pods.core.apple.ble.devices.airpods

import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.ble.devices.BaseBlePodsTest
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class AirPodsMax2Test : BaseBlePodsTest() {

    @Test
    fun `test AirPods Max 2 - right pod in use`() = runTest {
        create<AirPodsMax2>("07 19 01 2D 20 2B F6 8F 03 14 05 E7 17 3C 7D 65 B8 F3 94 45 12 90 EB E3 24 D3 3E") {
            pubPrefix shouldBe 0x01.toUByte()
            pubDeviceModel shouldBe 0x2D20.toUShort()
            pubStatus shouldBe 0x2B.toUByte()
            pubPodsBattery shouldBe 0xF6.toUByte()
            pubFlags shouldBe 0x8.toUShort()
            pubCaseBattery shouldBe 0xF.toUShort()
            pubCaseLidState shouldBe 0x03.toUByte()
            pubDeviceColor shouldBe 0x14.toUByte()
            pubSuffix shouldBe 0x05.toUByte()

            batteryHeadsetPercent shouldBe 0.6f

            isHeadsetBeingCharged shouldBe false
            isBeingWorn shouldBe true

            model shouldBe PodModel.AIRPODS_MAX2
        }
    }

    @Test
    fun `test AirPods Max 2 - status variations`() = runTest {
        create<AirPodsMax2>("07 19 01 2D 20 20 F6 8F 03 14 04 91 DF 89 19 E4 87 FB E9 DA 39 CE 26 BC AD 57 05") {
            pubStatus shouldBe 0x20.toUByte()
            batteryHeadsetPercent shouldBe 0.6f
            isHeadsetBeingCharged shouldBe false
            isBeingWorn shouldBe false
            model shouldBe PodModel.AIRPODS_MAX2
        }

        create<AirPodsMax2>("07 19 01 2D 20 21 F6 8F 03 14 04 54 5D 3F 3D 3D D9 6E F0 26 B9 0F 93 69 5F FB 46") {
            pubStatus shouldBe 0x21.toUByte()
            batteryHeadsetPercent shouldBe 0.6f
            isHeadsetBeingCharged shouldBe false
            isBeingWorn shouldBe false
            model shouldBe PodModel.AIRPODS_MAX2
        }

        create<AirPodsMax2>("07 19 01 2D 20 23 F6 8F 03 14 04 E1 8B 99 98 20 0B C3 C1 12 2D B0 43 98 94 D6 A0") {
            pubStatus shouldBe 0x23.toUByte()
            batteryHeadsetPercent shouldBe 0.6f
            isHeadsetBeingCharged shouldBe false
            isBeingWorn shouldBe true
            model shouldBe PodModel.AIRPODS_MAX2
        }

        // Synthetic: one earcup off, the other on (issue #548 sees 0x29 in this state on
        // both macOS and Android; we don't claim left vs right side semantics here).
        create<AirPodsMax2>("07 19 01 2D 20 29 F6 8F 03 14 04 E1 8B 99 98 20 0B C3 C1 12 2D B0 43 98 94 D6 A0") {
            pubStatus shouldBe 0x29.toUByte()
            batteryHeadsetPercent shouldBe 0.6f
            isHeadsetBeingCharged shouldBe false
            isBeingWorn shouldBe true
            model shouldBe PodModel.AIRPODS_MAX2
        }
    }
}
