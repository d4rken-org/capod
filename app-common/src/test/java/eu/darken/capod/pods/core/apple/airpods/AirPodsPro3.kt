package eu.darken.capod.pods.core.apple.airpods

import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.BaseAirPodsTest
import eu.darken.capod.pods.core.apple.HasAppleColor
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class AirPodsPro3Test : BaseAirPodsTest() {

    @Test
    fun `AirPods Pro 3`() = runTest {
        create<AirPodsPro3>("07 19 01 27 20 0B 99 8F 11 00 05 43 A8 85 17 07 FF 41 62 2C FE 5E 20 08 1A 52 40") {
            isLeftPodInEar shouldBe true
            isRightPodInEar shouldBe true

            isCaseCharging shouldBe false
            isRightPodCharging shouldBe false
            isLeftPodCharging shouldBe false
            batteryCasePercent shouldBe null

            podStyle.identifier shouldBe HasAppleColor.DeviceColor.WHITE.name

            model shouldBe PodDevice.Model.AIRPODS_PRO3
        }
    }
}