package eu.darken.capod.pods.core.apple.ble.devices.airpods

import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.ble.devices.BaseBlePodsTest
import eu.darken.capod.pods.core.apple.ble.devices.DualApplePods
import eu.darken.capod.pods.core.apple.ble.devices.HasAppleColor
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class AirPodsGen5WirelessTest : BaseBlePodsTest() {

    @Test
    fun `AirPods 5 Wireless Case in case, lid closed`() = runTest {
        create<AirPodsGen5Wireless>("07 19 01 30 20 55 A9 B9 5A 00 04 7E 88 A7 35 54 C2 34 41 4C 46 EC 23 9E BD A6 14") {
            pubPrefix shouldBe 0x01.toUByte()
            pubDeviceModel shouldBe 0x3020.toUShort()
            pubStatus shouldBe 0x55.toUByte()
            pubPodsBattery shouldBe 0xA9.toUByte()
            pubFlags shouldBe 0xB.toUShort()
            pubCaseBattery shouldBe 0x9.toUShort()
            pubCaseLidState shouldBe 0x5A.toUByte()
            pubDeviceColor shouldBe 0x00.toUByte()
            pubSuffix shouldBe 0x04.toUByte()

            isThisPodInThecase shouldBe true
            isOnePodInCase shouldBe true
            areBothPodsInCase shouldBe true

            isLeftPodMicrophone shouldBe true
            isRightPodMicrophone shouldBe false

            isLeftPodInEar shouldBe false
            isRightPodInEar shouldBe false

            batteryLeftPodPercent shouldBe 1.0f
            batteryRightPodPercent shouldBe 0.9f
            batteryCasePercent shouldBe 0.9f

            isLeftPodCharging shouldBe true
            isRightPodCharging shouldBe true
            isCaseCharging shouldBe false

            caseLidState shouldBe DualApplePods.LidState.CLOSED

            state shouldBe HasStateDetectionAirPods.ConnectionState.IDLE

            podStyle.identifier shouldBe HasAppleColor.DeviceColor.WHITE.name

            model shouldBe PodModel.AIRPODS_GEN5_WIRELESS
        }
    }

    @Test
    fun `AirPods 5 Wireless Case in case, lid opened, link dropped`() = runTest {
        // Pairs with the vector above: the battery byte is 0x9A here against 0xA9 there, but the
        // primary-pod bit flipped too, so the decoder reads the opposite nibble and both frames
        // report the same physical levels.
        create<AirPodsGen5Wireless>("07 19 01 30 20 25 9A B9 53 00 00 2A 46 EA D2 5A A6 33 38 D8 23 69 BD 57 C9 65 F7") {
            pubPrefix shouldBe 0x01.toUByte()
            pubDeviceModel shouldBe 0x3020.toUShort()
            pubStatus shouldBe 0x25.toUByte()
            pubPodsBattery shouldBe 0x9A.toUByte()
            pubFlags shouldBe 0xB.toUShort()
            pubCaseBattery shouldBe 0x9.toUShort()
            pubCaseLidState shouldBe 0x53.toUByte()
            pubDeviceColor shouldBe 0x00.toUByte()
            pubSuffix shouldBe 0x00.toUByte()

            isThisPodInThecase shouldBe false
            isOnePodInCase shouldBe false
            areBothPodsInCase shouldBe true

            isLeftPodMicrophone shouldBe true
            isRightPodMicrophone shouldBe false

            isLeftPodInEar shouldBe false
            isRightPodInEar shouldBe false

            batteryLeftPodPercent shouldBe 1.0f
            batteryRightPodPercent shouldBe 0.9f
            batteryCasePercent shouldBe 0.9f

            isLeftPodCharging shouldBe true
            isRightPodCharging shouldBe true
            isCaseCharging shouldBe false

            caseLidState shouldBe DualApplePods.LidState.OPEN

            state shouldBe HasStateDetectionAirPods.ConnectionState.DISCONNECTED

            podStyle.identifier shouldBe HasAppleColor.DeviceColor.WHITE.name

            model shouldBe PodModel.AIRPODS_GEN5_WIRELESS
        }
    }

    @Test
    fun `AirPods 5 Wireless Case one pod worn, one charging`() = runTest {
        // Only bit 4 is set, so caseLidState suppresses the stale lid byte to UNKNOWN. The factory
        // path then overrides it from cachedCaseState, which holds no usable in-case frame and so
        // falls back to NOT_IN_CASE. The suppression itself is covered by DualApplePodsTest.
        create<AirPodsGen5Wireless>("07 19 01 30 20 13 A9 A9 13 00 04 55 94 59 C6 1C C9 6D 13 4F EA D9 52 71 AE EB E4") {
            pubStatus shouldBe 0x13.toUByte()
            pubPodsBattery shouldBe 0xA9.toUByte()
            pubFlags shouldBe 0xA.toUShort()
            pubCaseBattery shouldBe 0x9.toUShort()
            pubCaseLidState shouldBe 0x13.toUByte()
            pubSuffix shouldBe 0x04.toUByte()

            isThisPodInThecase shouldBe false
            isOnePodInCase shouldBe true
            areBothPodsInCase shouldBe false

            isLeftPodMicrophone shouldBe false
            isRightPodMicrophone shouldBe true

            isLeftPodInEar shouldBe false
            isRightPodInEar shouldBe true

            batteryLeftPodPercent shouldBe 1.0f
            batteryRightPodPercent shouldBe 0.9f
            batteryCasePercent shouldBe 0.9f

            isLeftPodCharging shouldBe true
            isRightPodCharging shouldBe false
            isCaseCharging shouldBe false

            caseLidState shouldBe DualApplePods.LidState.NOT_IN_CASE

            state shouldBe HasStateDetectionAirPods.ConnectionState.IDLE

            model shouldBe PodModel.AIRPODS_GEN5_WIRELESS
        }
    }

    @Test
    fun `AirPods 5 Wireless Case both pods worn, playing`() = runTest {
        create<AirPodsGen5Wireless>("07 19 01 30 20 0B A9 8F 13 00 05 EA DB 3C 0E 2E 09 42 A7 22 FF B6 CD D7 FD E8 1A") {
            pubStatus shouldBe 0x0B.toUByte()
            pubPodsBattery shouldBe 0xA9.toUByte()
            pubFlags shouldBe 0x8.toUShort()
            pubCaseBattery shouldBe 0xF.toUShort()
            pubCaseLidState shouldBe 0x13.toUByte()
            pubSuffix shouldBe 0x05.toUByte()

            isThisPodInThecase shouldBe false
            isOnePodInCase shouldBe false
            areBothPodsInCase shouldBe false

            isLeftPodMicrophone shouldBe false
            isRightPodMicrophone shouldBe true

            isLeftPodInEar shouldBe true
            isRightPodInEar shouldBe true

            batteryLeftPodPercent shouldBe 1.0f
            batteryRightPodPercent shouldBe 0.9f
            batteryCasePercent shouldBe null

            isLeftPodCharging shouldBe false
            isRightPodCharging shouldBe false
            isCaseCharging shouldBe false

            caseLidState shouldBe DualApplePods.LidState.NOT_IN_CASE

            state shouldBe HasStateDetectionAirPods.ConnectionState.MUSIC

            model shouldBe PodModel.AIRPODS_GEN5_WIRELESS
        }
    }
}
