package eu.darken.capod.pods.core.apple.airpods

import eu.darken.capod.pods.core.DualPodDevice
import eu.darken.capod.pods.core.apple.BaseAirPodsTest
import eu.darken.capod.pods.core.apple.DualApplePods
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Test

class AirPodsProTest : BaseAirPodsTest() {

    @Test
    fun `test AirPods Pro - default changed and in case`() = runBlockingTest {
        create<AirPodsPro>("07 19 01 0E 20 54 AA B5 31 00 00 E0 0C A7 8A 60 4B D3 7D F4 60 4F 2C 73 E9 A7 F4") {

            rawPrefix shouldBe 0x01.toUByte()
            rawDeviceModel shouldBe 0x0e20.toUShort()
            rawStatus shouldBe 0x54.toUByte()
            rawPodsBattery shouldBe 0xAA.toUByte()
            rawCaseBattery shouldBe 0xB5.toUByte()
            rawCaseLidState shouldBe 0x31.toUByte()
            rawDeviceColor shouldBe 0x00.toUByte()
            rawSuffix shouldBe 0x00.toUByte()

            microPhonePod shouldBe DualPodDevice.Pod.RIGHT

            batteryLeftPodPercent shouldBe 1.0f
            batteryRightPodPercent shouldBe 1.0f

            isCaseCharging shouldBe false
            isRightPodCharging shouldBe true
            isLeftPodCharging shouldBe true
            batteryCasePercent shouldBe 0.5f

            deviceColor shouldBe DualApplePods.DeviceColor.WHITE
        }
    }

    @Test
    fun `test AirPods from my downstairs neighbour`() = runBlockingTest {
        create<AirPodsPro>("07 19 01 0E 20 00 F3 8F 02 00 04 79 C6 3F F9 C3 15 D9 11 A1 3C B1 58 66 B9 8B 67") {
            microPhonePod shouldBe DualPodDevice.Pod.RIGHT

            batteryLeftPodPercent shouldBe null
            batteryRightPodPercent shouldBe 0.3f

            isCaseCharging shouldBe false
            isRightPodCharging shouldBe false
            isLeftPodCharging shouldBe false
            batteryCasePercent shouldBe null

            deviceColor shouldBe DualApplePods.DeviceColor.WHITE
        }
    }

    // Test data from https://github.com/adolfintel/OpenPods/issues/34#issuecomment-565894487
    @Test
    fun `various AirPods Pro messages`() = runBlockingTest {
        create<AirPodsPro>("0719010e202b668f01000500000000000000000000000000000000") {
            batteryLeftPodPercent shouldBe 0.6f
            batteryRightPodPercent shouldBe 0.6f

            isCaseCharging shouldBe false
            isRightPodCharging shouldBe false
            isLeftPodCharging shouldBe false
            batteryCasePercent shouldBe null

            deviceColor shouldBe DualApplePods.DeviceColor.WHITE
        }

        create<AirPodsPro>("0719010e202b668f01000500000000000000000000000000000000") {
            batteryLeftPodPercent shouldBe 0.6f
            batteryRightPodPercent shouldBe 0.6f

            isCaseCharging shouldBe false
            isRightPodCharging shouldBe false
            isLeftPodCharging shouldBe false
            batteryCasePercent shouldBe null

            deviceColor shouldBe DualApplePods.DeviceColor.WHITE
        }

        create<AirPodsPro>("0719010e202b668f01000400000000000000000000000000000000") {
            batteryLeftPodPercent shouldBe 0.6f
            batteryRightPodPercent shouldBe 0.6f

            isCaseCharging shouldBe false
            isRightPodCharging shouldBe false
            isLeftPodCharging shouldBe false
            batteryCasePercent shouldBe null

            deviceColor shouldBe DualApplePods.DeviceColor.WHITE
        }

        create<AirPodsPro>("0719010e200b668f01000500000000000000000000000000000000") {
            batteryLeftPodPercent shouldBe 0.6f
            batteryRightPodPercent shouldBe 0.6f

            isCaseCharging shouldBe false
            isRightPodCharging shouldBe false
            isLeftPodCharging shouldBe false
            batteryCasePercent shouldBe null

            deviceColor shouldBe DualApplePods.DeviceColor.WHITE
        }

        create<AirPodsPro>("0719010e2003668f01000500000000000000000000000000000000") {
            batteryLeftPodPercent shouldBe 0.6f
            batteryRightPodPercent shouldBe 0.6f

            isCaseCharging shouldBe false
            isRightPodCharging shouldBe false
            isLeftPodCharging shouldBe false
            batteryCasePercent shouldBe null

            deviceColor shouldBe DualApplePods.DeviceColor.WHITE
        }

        create<AirPodsPro>("0719010e2001668f01000500000000000000000000000000000000") {
            batteryLeftPodPercent shouldBe 0.6f
            batteryRightPodPercent shouldBe 0.6f

            isCaseCharging shouldBe false
            isRightPodCharging shouldBe false
            isLeftPodCharging shouldBe false
            batteryCasePercent shouldBe null

            deviceColor shouldBe DualApplePods.DeviceColor.WHITE
        }

        create<AirPodsPro>("0719010e2009668f01000500000000000000000000000000000000") {
            batteryLeftPodPercent shouldBe 0.6f
            batteryRightPodPercent shouldBe 0.6f

            isCaseCharging shouldBe false
            isRightPodCharging shouldBe false
            isLeftPodCharging shouldBe false
            batteryCasePercent shouldBe null

            deviceColor shouldBe DualApplePods.DeviceColor.WHITE
        }

        create<AirPodsPro>("0719010e2053669653000500000000000000000000000000000000") {
            batteryLeftPodPercent shouldBe 0.6f
            batteryRightPodPercent shouldBe 0.6f

            isCaseCharging shouldBe false
            isRightPodCharging shouldBe true
            isLeftPodCharging shouldBe false
            batteryCasePercent shouldBe 0.6f

            deviceColor shouldBe DualApplePods.DeviceColor.WHITE
        }

        create<AirPodsPro>("0719010e203366a602000500000000000000000000000000000000") {
            batteryLeftPodPercent shouldBe 0.6f
            batteryRightPodPercent shouldBe 0.6f

            isCaseCharging shouldBe false
            isRightPodCharging shouldBe true
            isLeftPodCharging shouldBe false
            batteryCasePercent shouldBe 0.6f

            deviceColor shouldBe DualApplePods.DeviceColor.WHITE
        }

        create<AirPodsPro>("0719010e202b768f02000500000000000000000000000000000000") {
            batteryLeftPodPercent shouldBe 0.6f
            batteryRightPodPercent shouldBe 0.7f

            isCaseCharging shouldBe false
            isRightPodCharging shouldBe false
            isLeftPodCharging shouldBe false
            batteryCasePercent shouldBe null

            deviceColor shouldBe DualApplePods.DeviceColor.WHITE
        }
    }

    // Test data from https://github.com/adolfintel/OpenPods/issues/39#issuecomment-557664269
    @Test
    fun `fake airpods`() = runBlockingTest {
        create<AirPodsGen1>("071901022055AA563100006FE4DF10AF106081033B76D9C7112288") {
            batteryLeftPodPercent shouldBe 1.0f
            batteryRightPodPercent shouldBe 1.0f

            isCaseCharging shouldBe true
            isRightPodCharging shouldBe true
            isLeftPodCharging shouldBe false
            batteryCasePercent shouldBe 0.6f

            deviceColor shouldBe DualApplePods.DeviceColor.WHITE
        }
    }
}