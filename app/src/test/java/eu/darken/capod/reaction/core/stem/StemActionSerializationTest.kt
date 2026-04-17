package eu.darken.capod.reaction.core.stem

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class StemActionSerializationTest : BaseTest() {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        classDiscriminator = "type"
    }

    @Test
    fun `every action round-trips through Json`() {
        for (action in StemAction.all) {
            val encoded = json.encodeToString(StemAction.serializer(), action)
            val decoded = json.decodeFromString(StemAction.serializer(), encoded)
            decoded shouldBe action
        }
    }

    @Test
    fun `wire form uses type discriminator with legacy-style names`() {
        json.encodeToString(StemAction.serializer(), StemAction.PlayPause) shouldBe """{"type":"PLAY_PAUSE"}"""
        json.encodeToString(StemAction.serializer(), StemAction.CycleAnc) shouldBe """{"type":"CYCLE_ANC"}"""
        json.encodeToString(StemAction.serializer(), StemAction.ToggleAncTransparency) shouldBe """{"type":"TOGGLE_ANC_TRANSPARENCY"}"""
        json.encodeToString(StemAction.serializer(), StemAction.None) shouldBe """{"type":"NONE"}"""
    }

    @Test
    fun `StemActionsConfig round-trips with new actions`() {
        val original = StemActionsConfig(
            leftSingle = StemAction.PlayPause,
            rightSingle = StemAction.NoAction,
            leftLong = StemAction.CycleAnc,
            rightLong = StemAction.ToggleAncTransparency,
            leftDouble = StemAction.Stop,
            rightDouble = StemAction.FastForward,
            leftTriple = StemAction.Rewind,
            rightTriple = StemAction.MuteToggle,
        )
        val encoded = json.encodeToString(StemActionsConfig.serializer(), original)
        val decoded = json.decodeFromString(StemActionsConfig.serializer(), encoded)
        decoded shouldBe original
    }
}
