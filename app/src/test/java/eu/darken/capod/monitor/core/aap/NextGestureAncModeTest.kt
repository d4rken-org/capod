package eu.darken.capod.monitor.core.aap

import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting.AncMode.Value
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class NextGestureAncModeTest : BaseTest() {

    private val allFour = listOf(Value.OFF, Value.ON, Value.TRANSPARENCY, Value.ADAPTIVE)

    private fun state(
        current: Value,
        supported: List<Value> = allFour,
        mask: Int? = 0x0F,
        allowOff: Boolean = true,
    ) = EffectiveAncState(
        current = current,
        supported = supported,
        cycleMask = mask,
        allowOffEnabled = allowOff,
    )

    @Test
    fun `null mask is a no-op`() {
        nextGestureAncMode(state(current = Value.ON, mask = null)).shouldBeNull()
    }

    @Test
    fun `zero mask is a no-op`() {
        nextGestureAncMode(state(current = Value.ON, mask = 0x00)).shouldBeNull()
    }

    @Test
    fun `single-mode mask is a no-op`() {
        // Only ON enabled
        nextGestureAncMode(state(current = Value.ON, mask = 0x02)).shouldBeNull()
    }

    @Test
    fun `two-mode mask cycles between them`() {
        // ON + TRANSPARENCY
        nextGestureAncMode(state(current = Value.ON, mask = 0x06)) shouldBe Value.TRANSPARENCY
        nextGestureAncMode(state(current = Value.TRANSPARENCY, mask = 0x06)) shouldBe Value.ON
    }

    @Test
    fun `four-mode mask walks the full cycle`() {
        nextGestureAncMode(state(current = Value.OFF)) shouldBe Value.ON
        nextGestureAncMode(state(current = Value.ON)) shouldBe Value.TRANSPARENCY
        nextGestureAncMode(state(current = Value.TRANSPARENCY)) shouldBe Value.ADAPTIVE
        nextGestureAncMode(state(current = Value.ADAPTIVE)) shouldBe Value.OFF
    }

    @Test
    fun `allowOff=false strips OFF from the cycle`() {
        nextGestureAncMode(state(current = Value.ADAPTIVE, allowOff = false)) shouldBe Value.ON
        // Current=OFF is out-of-cycle when allowOff=false → jumps to first cycle mode
        nextGestureAncMode(state(current = Value.OFF, allowOff = false)) shouldBe Value.ON
    }

    @Test
    fun `current out of cycle jumps to first cycle mode`() {
        // Cycle = TRANSPARENCY + ADAPTIVE only, but current is ON
        nextGestureAncMode(state(current = Value.ON, mask = 0x0C)) shouldBe Value.TRANSPARENCY
    }

    @Test
    fun `unsupported modes are excluded even if mask enables them`() {
        // Device only supports OFF + ON, but mask says OFF+ON+TRANSPARENCY
        val supported = listOf(Value.OFF, Value.ON)
        nextGestureAncMode(state(current = Value.ON, supported = supported, mask = 0x07)) shouldBe Value.OFF
    }
}
