package eu.darken.capod.pods.core.apple.ble.devices.airpods

import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.ble.devices.BaseBlePodsTest
import eu.darken.capod.pods.core.apple.ble.devices.DualApplePods
import eu.darken.capod.pods.core.apple.ble.devices.HasAppleColor
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class AirPodsGen5Test : BaseBlePodsTest() {

    @Test
    fun `AirPods 5 in case, lid open`() = runTest {
        create<AirPodsGen5>("07 19 01 36 20 15 99 F9 51 00 04 FB 0F 0C 65 33 51 1A CE 8F 04 32 66 9B D1 BD 4E") {
            pubPrefix shouldBe 0x01.toUByte()
            pubDeviceModel shouldBe 0x3620.toUShort()
            pubStatus shouldBe 0x15.toUByte()
            pubPodsBattery shouldBe 0x99.toUByte()
            pubFlags shouldBe 0xF.toUShort()
            pubCaseBattery shouldBe 0x9.toUShort()
            pubCaseLidState shouldBe 0x51.toUByte()
            pubDeviceColor shouldBe 0x00.toUByte()
            pubSuffix shouldBe 0x04.toUByte()

            isThisPodInThecase shouldBe false
            isOnePodInCase shouldBe true
            areBothPodsInCase shouldBe true

            isLeftPodMicrophone shouldBe false
            isRightPodMicrophone shouldBe true

            isLeftPodInEar shouldBe false
            isRightPodInEar shouldBe false

            batteryLeftPodPercent shouldBe 0.9f
            batteryRightPodPercent shouldBe 0.9f
            batteryCasePercent shouldBe 0.9f

            isLeftPodCharging shouldBe true
            isRightPodCharging shouldBe true
            isCaseCharging shouldBe true

            caseLidState shouldBe DualApplePods.LidState.OPEN

            state shouldBe HasStateDetectionAirPods.ConnectionState.IDLE

            podStyle.identifier shouldBe HasAppleColor.DeviceColor.WHITE.name

            model shouldBe PodModel.AIRPODS_GEN5
        }
    }

    @Test
    fun `AirPods 5 in case, lid open, other pod broadcasting`() = runTest {
        // Same physical state as above, but the other pod is broadcasting. Both frames still have
        // to agree on which pod is the microphone, even though the primary bit flipped with it.
        create<AirPodsGen5>("07 19 01 36 20 75 99 F9 51 00 04 FE FE 67 AA BA BE 80 C9 C3 2E 81 10 9E CD A7 46") {
            pubPrefix shouldBe 0x01.toUByte()
            pubDeviceModel shouldBe 0x3620.toUShort()
            pubStatus shouldBe 0x75.toUByte()
            pubPodsBattery shouldBe 0x99.toUByte()
            pubFlags shouldBe 0xF.toUShort()
            pubCaseBattery shouldBe 0x9.toUShort()
            pubCaseLidState shouldBe 0x51.toUByte()
            pubDeviceColor shouldBe 0x00.toUByte()
            pubSuffix shouldBe 0x04.toUByte()

            isThisPodInThecase shouldBe true
            isOnePodInCase shouldBe true
            areBothPodsInCase shouldBe true

            isLeftPodMicrophone shouldBe false
            isRightPodMicrophone shouldBe true

            isLeftPodInEar shouldBe false
            isRightPodInEar shouldBe false

            batteryLeftPodPercent shouldBe 0.9f
            batteryRightPodPercent shouldBe 0.9f
            batteryCasePercent shouldBe 0.9f

            isLeftPodCharging shouldBe true
            isRightPodCharging shouldBe true
            isCaseCharging shouldBe true

            caseLidState shouldBe DualApplePods.LidState.OPEN

            state shouldBe HasStateDetectionAirPods.ConnectionState.IDLE

            podStyle.identifier shouldBe HasAppleColor.DeviceColor.WHITE.name

            model shouldBe PodModel.AIRPODS_GEN5
        }
    }

    @Test
    fun `AirPods 5 in case, lid closed`() = runTest {
        create<AirPodsGen5>("07 19 01 36 20 15 99 F9 59 00 04 E1 5E 66 15 6F 0B 36 9A C6 17 43 56 B7 34 28 7D") {
            pubPrefix shouldBe 0x01.toUByte()
            pubDeviceModel shouldBe 0x3620.toUShort()
            pubStatus shouldBe 0x15.toUByte()
            pubPodsBattery shouldBe 0x99.toUByte()
            pubFlags shouldBe 0xF.toUShort()
            pubCaseBattery shouldBe 0x9.toUShort()
            pubCaseLidState shouldBe 0x59.toUByte()
            pubDeviceColor shouldBe 0x00.toUByte()
            pubSuffix shouldBe 0x04.toUByte()

            isThisPodInThecase shouldBe false
            isOnePodInCase shouldBe true
            areBothPodsInCase shouldBe true

            isLeftPodMicrophone shouldBe false
            isRightPodMicrophone shouldBe true

            isLeftPodInEar shouldBe false
            isRightPodInEar shouldBe false

            batteryLeftPodPercent shouldBe 0.9f
            batteryRightPodPercent shouldBe 0.9f
            batteryCasePercent shouldBe 0.9f

            isLeftPodCharging shouldBe true
            isRightPodCharging shouldBe true
            isCaseCharging shouldBe true

            caseLidState shouldBe DualApplePods.LidState.CLOSED

            state shouldBe HasStateDetectionAirPods.ConnectionState.IDLE

            podStyle.identifier shouldBe HasAppleColor.DeviceColor.WHITE.name

            model shouldBe PodModel.AIRPODS_GEN5
        }
    }

    @Test
    fun `AirPods 5 disconnected`() = runTest {
        create<AirPodsGen5>("07 19 01 36 20 14 99 F9 58 00 00 C8 84 3F AE C5 17 15 92 3A 14 B6 65 31 84 66 86") {
            pubPrefix shouldBe 0x01.toUByte()
            pubDeviceModel shouldBe 0x3620.toUShort()
            pubStatus shouldBe 0x14.toUByte()
            pubPodsBattery shouldBe 0x99.toUByte()
            pubFlags shouldBe 0xF.toUShort()
            pubCaseBattery shouldBe 0x9.toUShort()
            pubCaseLidState shouldBe 0x58.toUByte()
            pubDeviceColor shouldBe 0x00.toUByte()
            pubSuffix shouldBe 0x00.toUByte()

            isThisPodInThecase shouldBe false
            isOnePodInCase shouldBe true
            areBothPodsInCase shouldBe true

            isLeftPodMicrophone shouldBe false
            isRightPodMicrophone shouldBe true

            isLeftPodInEar shouldBe false
            isRightPodInEar shouldBe false

            batteryLeftPodPercent shouldBe 0.9f
            batteryRightPodPercent shouldBe 0.9f
            batteryCasePercent shouldBe 0.9f

            isLeftPodCharging shouldBe true
            isRightPodCharging shouldBe true
            isCaseCharging shouldBe true

            caseLidState shouldBe DualApplePods.LidState.CLOSED

            state shouldBe HasStateDetectionAirPods.ConnectionState.DISCONNECTED

            podStyle.identifier shouldBe HasAppleColor.DeviceColor.WHITE.name

            model shouldBe PodModel.AIRPODS_GEN5
        }
    }
}
