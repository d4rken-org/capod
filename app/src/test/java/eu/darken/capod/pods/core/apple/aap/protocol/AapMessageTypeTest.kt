package eu.darken.capod.pods.core.apple.aap.protocol

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class AapMessageTypeTest : BaseTest() {

    @Test
    fun `no duplicate opcodes`() {
        val grouped = AapMessageType.entries.groupBy { it.value }
        val duplicates = grouped.filterValues { it.size > 1 }
        duplicates shouldBe emptyMap()
    }

    @Test
    fun `every entry round-trips via byValue`() {
        for (type in AapMessageType.entries) {
            AapMessageType.byValue(type.value) shouldBe type
        }
    }

    @Test
    fun `unknown opcodes return null`() {
        AapMessageType.byValue(0x7FFF).shouldBeNull()
        AapMessageType.byValue(-1).shouldBeNull()
        AapMessageType.byValue(0x0018).shouldBeNull() // Gap in the known catalog
    }

    @Test
    fun `load-bearing opcodes keep their values`() {
        AapMessageType.BATTERY_INFO.value shouldBe 0x0004
        AapMessageType.EAR_DETECTION.value shouldBe 0x0006
        AapMessageType.BUD_ROLE.value shouldBe 0x0008
        AapMessageType.CONTROL.value shouldBe 0x0009
        AapMessageType.AUDIO_SOURCE.value shouldBe 0x000E
        AapMessageType.BUDDY_COMMAND.value shouldBe 0x0017
        AapMessageType.STEM_PRESS.value shouldBe 0x0019
        AapMessageType.RENAME.value shouldBe 0x001A
        AapMessageType.INFORMATION.value shouldBe 0x001D
        AapMessageType.CONNECTED_DEVICES.value shouldBe 0x002E
        AapMessageType.MAGIC_KEYS_REQUEST.value shouldBe 0x0030
        AapMessageType.MAGIC_KEYS.value shouldBe 0x0031
        AapMessageType.CONVERSATIONAL_AWARENESS.value shouldBe 0x004B
        AapMessageType.SOURCE_FEATURE_CAPABILITIES.value shouldBe 0x004D
        AapMessageType.PME_CONFIG.value shouldBe 0x0053
    }
}
