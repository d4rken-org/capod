package eu.darken.capod.pods.core.apple.misc

import eu.darken.capod.pods.core.apple.BaseAirPodsTest
import eu.darken.capod.pods.core.apple.DualAirPods
import eu.darken.capod.pods.core.apple.HasAppleColor
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class FakeAirPodsGen3Test : BaseAirPodsTest() {

    @Test
    fun `charging in case`() = runTest {
        create<FakeAirPodsGen3>("07 13 01 13 20 75 AA 37 34 00 10 00 E4 E4 64 00 00 00 00 00 00") {
            batteryLeftPodPercent shouldBe 1f
            batteryRightPodPercent shouldBe 1f

            isCaseCharging shouldBe false
            isLeftPodCharging shouldBe true
            isRightPodCharging shouldBe true

            isLeftPodInEar shouldBe false
            isRightPodInEar shouldBe false
            batteryCasePercent shouldBe 0.7f

            caseLidState shouldBe DualAirPods.LidState.OPEN

            state shouldBe DualAirPods.ConnectionState.UNKNOWN

            podStyle.identifier shouldBe HasAppleColor.DeviceColor.WHITE.name

        }
    }
}