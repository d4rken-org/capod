package eu.darken.capod.pods.core.apple.ble.devices.beats

import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.ble.devices.BaseBlePodsTest
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class Beats360Test : BaseBlePodsTest() {

    @Test
    fun `default Beats360`() = runTest {
        create<Beats360>("07 19 01 38 20 00 07 80 04 02 00 01 7D B1 76 1F 5C D6 93 D7 8B 71 77 0A 81 7B AD") {
            pubDeviceModel shouldBe 0x3820.toUShort()
            batteryHeadsetPercent shouldBe 0.7f
            model shouldBe PodModel.BEATS_360
        }
    }

    @Test
    fun `model number maps to Beats 360`() {
        PodModel.fromModelNumber("A3577") shouldBe PodModel.BEATS_360
    }
}
