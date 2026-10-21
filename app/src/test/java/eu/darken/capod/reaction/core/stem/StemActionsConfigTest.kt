package eu.darken.capod.reaction.core.stem

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class StemActionsConfigTest : BaseTest() {

    @Test
    fun `uses finds the action on every slot`() {
        val va = StemAction.VoiceAssistant
        listOf(
            StemActionsConfig(leftSingle = va),
            StemActionsConfig(leftDouble = va),
            StemActionsConfig(leftTriple = va),
            StemActionsConfig(leftLong = va),
            StemActionsConfig(rightSingle = va),
            StemActionsConfig(rightDouble = va),
            StemActionsConfig(rightTriple = va),
            StemActionsConfig(rightLong = va),
        ).forEach { config ->
            config.uses(va) shouldBe true
        }
    }

    @Test
    fun `uses is false when no slot has the action`() {
        StemActionsConfig().uses(StemAction.VoiceAssistant) shouldBe false
        StemActionsConfig(
            leftSingle = StemAction.PlayPause,
            rightSingle = StemAction.NoAction,
            leftLong = StemAction.CycleAnc,
            rightLong = StemAction.ToggleAncTransparency,
        ).uses(StemAction.VoiceAssistant) shouldBe false
    }
}
