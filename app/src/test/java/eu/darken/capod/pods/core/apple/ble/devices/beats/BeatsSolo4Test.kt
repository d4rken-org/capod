package eu.darken.capod.pods.core.apple.ble.devices.beats

import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.ble.devices.BaseBlePodsTest
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class BeatsSolo4Test : BaseBlePodsTest() {

    // TODO Replace with real device data
    @Test
    fun `default BeatsSolo4`() = runTest {
        create<BeatsSolo4>("07 19 01 25 20 62 04 80 01 0F 40 0D 70 50 16 F2 40 83 16 BF 10 16 34 9B 74 84 E8") {
            pubDeviceModel shouldBe 0x2520.toUShort()
            batteryHeadsetPercent shouldBe 0.4f
            model shouldBe PodModel.BEATS_SOLO_4
        }
    }
}
