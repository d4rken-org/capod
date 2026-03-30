package eu.darken.capod.pods.core.apple.ble.devices.beats

import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.ble.devices.BaseBlePodsTest
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class BeatsStudioBudsPlusTest : BaseBlePodsTest() {

    // TODO Replace with real device data
    @Test
    fun `default BeatsStudioBudsPlus`() = runTest {
        create<BeatsStudioBudsPlus>("07 19 01 16 20 20 FA 8F 01 00 24 9B 9B 23 52 60 5A 8C 32 1C A5 C2 81 51 82 AF C8") {
            pubDeviceModel shouldBe 0x1620.toUShort()
            batteryLeftPodPercent shouldBe 1.0f
            batteryRightPodPercent shouldBe null
            model shouldBe PodModel.BEATS_STUDIO_BUDS_PLUS
        }
    }
}
