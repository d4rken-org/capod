package eu.darken.capod.pods.core.apple.aap.protocol

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class AapControlIdTest : BaseTest() {

    @Test
    fun `no duplicate control IDs`() {
        val grouped = AapControlId.entries.groupBy { it.value }
        val duplicates = grouped.filterValues { it.size > 1 }
        duplicates shouldBe emptyMap()
    }

    @Test
    fun `every entry round-trips via byValue`() {
        for (id in AapControlId.entries) {
            AapControlId.byValue(id.value) shouldBe id
        }
    }

    @Test
    fun `unknown control IDs return null`() {
        AapControlId.byValue(0x7F).shouldBeNull()
        AapControlId.byValue(-1).shouldBeNull()
        AapControlId.byValue(0x50).shouldBeNull() // Past the known catalog
    }

    @Test
    fun `load-bearing control IDs keep their values`() {
        AapControlId.MIC_MODE.value shouldBe 0x01
        AapControlId.IN_EAR_DETECTION.value shouldBe 0x0A
        AapControlId.LISTEN_MODE.value shouldBe 0x0D
        AapControlId.DOUBLE_CLICK_INTERVAL.value shouldBe 0x17
        AapControlId.CLICK_AND_HOLD_INTERVAL.value shouldBe 0x18
        AapControlId.LISTENING_MODE_CONFIGS.value shouldBe 0x1A
        AapControlId.ONE_BUD_ANC_MODE.value shouldBe 0x1B
        AapControlId.CHIME_VOLUME.value shouldBe 0x1F
        AapControlId.VOLUME_SWIPE_INTERVAL.value shouldBe 0x23
        AapControlId.CALL_MANAGEMENT_CONFIG.value shouldBe 0x24
        AapControlId.VOLUME_SWIPE_MODE.value shouldBe 0x25
        AapControlId.ADAPTIVE_VOLUME.value shouldBe 0x26
        AapControlId.CONVERSATION_DETECT.value shouldBe 0x28
        AapControlId.AUTO_ANC_STRENGTH.value shouldBe 0x2E
        AapControlId.IN_CASE_TONE.value shouldBe 0x31
        AapControlId.ALLOW_OFF_OPTION.value shouldBe 0x34
        AapControlId.SLEEP_DETECTION.value shouldBe 0x35
        AapControlId.RAW_GESTURES_CONFIG.value shouldBe 0x39
        AapControlId.DYNAMIC_END_OF_CHARGE.value shouldBe 0x3B
    }
}
