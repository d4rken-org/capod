package eu.darken.capod.pods.core.apple.airpods

import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.BaseAirPodsTest
import eu.darken.capod.pods.core.apple.HasAppleColor
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class AirPodsProTest : BaseAirPodsTest() {

    @Test
    fun `test AirPods Pro - default changed and in case`() = runTest {
        create<AirPodsPro>("07 19 01 0E 20 54 AA B5 31 00 00 E0 0C A7 8A 60 4B D3 7D F4 60 4F 2C 73 E9 A7 F4") {

            pubPrefix shouldBe 0x01.toUByte()
            pubDeviceModel shouldBe 0x0e20.toUShort()
            pubStatus shouldBe 0x54.toUByte()
            pubPodsBattery shouldBe 0xAA.toUByte()
            pubFlags shouldBe 0xB.toUShort()
            pubCaseBattery shouldBe 0x5.toUShort()
            pubCaseLidState shouldBe 0x31.toUByte()
            pubDeviceColor shouldBe 0x00.toUByte()
            pubSuffix shouldBe 0x00.toUByte()

            isLeftPodMicrophone shouldBe true
            isRightPodMicrophone shouldBe false

            batteryLeftPodPercent shouldBe 1.0f
            batteryRightPodPercent shouldBe 1.0f

            isCaseCharging shouldBe false
            isRightPodCharging shouldBe true
            isLeftPodCharging shouldBe true
            batteryCasePercent shouldBe 0.5f

            podStyle.identifier shouldBe HasAppleColor.DeviceColor.WHITE.name

            model shouldBe PodDevice.Model.AIRPODS_PRO
        }
    }

    @Test
    fun `test AirPods from my downstairs neighbour`() = runTest {
        create<AirPodsPro>("07 19 01 0E 20 00 F3 8F 02 00 04 79 C6 3F F9 C3 15 D9 11 A1 3C B1 58 66 B9 8B 67") {
            isLeftPodMicrophone shouldBe false
            isRightPodMicrophone shouldBe true

            batteryLeftPodPercent shouldBe null
            batteryRightPodPercent shouldBe 0.3f

            isCaseCharging shouldBe false
            isRightPodCharging shouldBe false
            isLeftPodCharging shouldBe false
            batteryCasePercent shouldBe null
        }
    }

    // Test data from https://github.com/adolfintel/OpenPods/issues/34#issuecomment-565894487
    @Test
    fun `various AirPods Pro messages`() = runTest {
        create<AirPodsPro>("0719010e202b668f01000500000000000000000000000000000000") {
            batteryLeftPodPercent shouldBe 0.6f
            batteryRightPodPercent shouldBe 0.6f

            isCaseCharging shouldBe false
            isRightPodCharging shouldBe false
            isLeftPodCharging shouldBe false
            batteryCasePercent shouldBe null
        }

        create<AirPodsPro>("0719010e202b668f01000500000000000000000000000000000000") {
            batteryLeftPodPercent shouldBe 0.6f
            batteryRightPodPercent shouldBe 0.6f

            isCaseCharging shouldBe false
            isRightPodCharging shouldBe false
            isLeftPodCharging shouldBe false
            batteryCasePercent shouldBe null
        }

        create<AirPodsPro>("0719010e202b668f01000400000000000000000000000000000000") {
            batteryLeftPodPercent shouldBe 0.6f
            batteryRightPodPercent shouldBe 0.6f

            isCaseCharging shouldBe false
            isRightPodCharging shouldBe false
            isLeftPodCharging shouldBe false
            batteryCasePercent shouldBe null
        }

        create<AirPodsPro>("0719010e200b668f01000500000000000000000000000000000000") {
            batteryLeftPodPercent shouldBe 0.6f
            batteryRightPodPercent shouldBe 0.6f

            isCaseCharging shouldBe false
            isRightPodCharging shouldBe false
            isLeftPodCharging shouldBe false
            batteryCasePercent shouldBe null
        }

        create<AirPodsPro>("0719010e2003668f01000500000000000000000000000000000000") {
            batteryLeftPodPercent shouldBe 0.6f
            batteryRightPodPercent shouldBe 0.6f

            isCaseCharging shouldBe false
            isRightPodCharging shouldBe false
            isLeftPodCharging shouldBe false
            batteryCasePercent shouldBe null
        }

        create<AirPodsPro>("0719010e2001668f01000500000000000000000000000000000000") {
            batteryLeftPodPercent shouldBe 0.6f
            batteryRightPodPercent shouldBe 0.6f

            isCaseCharging shouldBe false
            isRightPodCharging shouldBe false
            isLeftPodCharging shouldBe false
            batteryCasePercent shouldBe null
        }

        create<AirPodsPro>("0719010e2009668f01000500000000000000000000000000000000") {
            batteryLeftPodPercent shouldBe 0.6f
            batteryRightPodPercent shouldBe 0.6f

            isCaseCharging shouldBe false
            isRightPodCharging shouldBe false
            isLeftPodCharging shouldBe false
            batteryCasePercent shouldBe null
        }

        create<AirPodsPro>("0719010e2053669653000500000000000000000000000000000000") {
            batteryLeftPodPercent shouldBe 0.6f
            batteryRightPodPercent shouldBe 0.6f

            isCaseCharging shouldBe false
            isRightPodCharging shouldBe true
            isLeftPodCharging shouldBe false
            batteryCasePercent shouldBe 0.6f
        }

        create<AirPodsPro>("0719010e203366a602000500000000000000000000000000000000") {
            batteryLeftPodPercent shouldBe 0.6f
            batteryRightPodPercent shouldBe 0.6f

            isCaseCharging shouldBe false
            isRightPodCharging shouldBe true
            isLeftPodCharging shouldBe false
            batteryCasePercent shouldBe 0.6f
        }

        create<AirPodsPro>("0719010e202b768f02000500000000000000000000000000000000") {
            batteryLeftPodPercent shouldBe 0.6f
            batteryRightPodPercent shouldBe 0.7f

            isCaseCharging shouldBe false
            isRightPodCharging shouldBe false
            isLeftPodCharging shouldBe false
            batteryCasePercent shouldBe 0.6f
        }
    }

    // Test data from https://github.com/adolfintel/OpenPods/issues/39#issuecomment-557664269
    @Test
    fun `fake airpods`() = runTest {
        create<AirPodsGen1>("071901022055AA563100006FE4DF10AF106081033B76D9C7112288") {
            batteryLeftPodPercent shouldBe 1.0f
            batteryRightPodPercent shouldBe 1.0f

            isCaseCharging shouldBe true
            isRightPodCharging shouldBe true
            isLeftPodCharging shouldBe false
            batteryCasePercent shouldBe 0.6f
        }
    }

    @Test
    fun `left pod has no data`() = runTest {
        create<AirPodsPro>("07 19 01 0E 20 0B F9 8F 03 00 05 5B 59 67 4C F7 F3 EF 01 BA F4 92 1B 26 E4 90 40") {
            pubPodsBattery shouldBe 0xF9.toUByte()

            batteryLeftPodPercent shouldBe null
            batteryRightPodPercent shouldBe 0.9f

            isCaseCharging shouldBe false
            isRightPodCharging shouldBe false
            isLeftPodCharging shouldBe false
            batteryCasePercent shouldBe null
        }
    }
}