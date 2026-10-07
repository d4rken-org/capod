package eu.darken.capod.pods.core.apple.aap.devices

import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.protocol.AapCommand
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import eu.darken.capod.pods.core.apple.aap.protocol.DefaultAapDeviceProfile
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class ConnectionPreferenceTest : BaseTest() {
    private val profile = DefaultAapDeviceProfile(PodModel.AIRPODS_PRO3)
    private fun hex(bytes: ByteArray) = bytes.joinToString(" ") { "%02x".format(it) }

    @Test
    fun `Automatic allows accessory links while preserving captured smart routing commands`() {
        DefaultAapDeviceProfile(PodModel.AIRPODS_GEN2)
            .encodeCommands(AapCommand.SetConnectionPreference(AapSetting.ConnectionPreference.Mode.AUTOMATIC))
            .map(::hex) shouldBe listOf(
            "04 00 04 00 09 00 36 01 00 00 00",
            "04 00 04 00 09 00 20 01 00 00 00",
            "04 00 04 00 44 00 04 00 02 00 03 06",
            "04 00 04 00 2d 00",
        )
    }

    @Test
    fun `Last connected enables accessory links and sends last connected routing context`() {
        profile.encodeCommands(AapCommand.SetConnectionPreference(AapSetting.ConnectionPreference.Mode.LAST_CONNECTED))
            .map(::hex) shouldBe listOf(
            "04 00 04 00 09 00 36 01 00 00 00",
            "04 00 04 00 09 00 20 02 00 00 00",
            "04 00 04 00 44 00 04 00 02 00 03 08",
        )
    }

    @Test
    fun `Off suppresses accessory auto connect without inventing other routing values`() {
        profile.encodeCommands(AapCommand.SetConnectionPreference(AapSetting.ConnectionPreference.Mode.OFF))
            .map(::hex) shouldBe listOf("04 00 04 00 09 00 36 02 00 00 00")
    }

    @Test
    fun `non AirPods models cannot send routing commands`() {
        shouldThrow<IllegalArgumentException> {
            DefaultAapDeviceProfile(PodModel.BEATS_FLEX).encodeCommands(
                AapCommand.SetConnectionPreference(AapSetting.ConnectionPreference.Mode.AUTOMATIC),
            )
        }
    }

}
