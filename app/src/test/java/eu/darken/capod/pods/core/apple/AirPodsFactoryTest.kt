package eu.darken.capod.pods.core.apple

import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.airpods.AirPodsGen1
import eu.darken.capod.pods.core.apple.airpods.AirPodsPro
import eu.darken.capod.pods.core.apple.misc.UnknownAppleDevice
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.instanceOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class AirPodsFactoryTest : BaseAirPodsTest() {

    @Test
    fun `create AirPodsGen1`() = runTest {
        create<DualApplePods>("07 19 01 02 20 55 AA 56 31 00 00 6F E4 DF 10 AF 10 60 81 03 3B 76 D9 C7 11 22 88") {
            this shouldBe instanceOf<AirPodsGen1>()
        }
    }

    @Test
    fun `create AirPodsPro`() = runTest {
        create<DualApplePods>("07 19 01 0E 20 2B 99 8F 01 00 >09< 10 30 EE F3 41 B5 D8 9F A3 B0 B4 17 9F 85 97 5F") {
            this shouldBe instanceOf<AirPodsPro>()
        }
    }

    @Test
    fun `unknown AppleDevice`() = runTest {
        create<ApplePods>("07 19 01 FF FF 2B 99 8F 01 00 >09< 10 30 EE F3 41 B5 D8 9F A3 B0 B4 17 9F 85 97 5F") {
            this shouldBe instanceOf<UnknownAppleDevice>()
        }
    }

    @Test
    fun `invalid data`() = runTest {
        create<PodDevice?>("abcd") {
            this shouldBe null
        }
    }
}