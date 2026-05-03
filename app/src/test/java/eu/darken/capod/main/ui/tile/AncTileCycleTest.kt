package eu.darken.capod.main.ui.tile

import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class AncTileCycleTest : BaseTest() {

    private val off = AapSetting.AncMode.Value.OFF
    private val on = AapSetting.AncMode.Value.ON
    private val tx = AapSetting.AncMode.Value.TRANSPARENCY
    private val ad = AapSetting.AncMode.Value.ADAPTIVE

    @Test
    fun `empty visible list returns null`() {
        pickNextMode(visible = emptyList(), current = on, pending = null) shouldBe null
    }

    @Test
    fun `single visible mode returns same mode`() {
        pickNextMode(visible = listOf(on), current = on, pending = null) shouldBe on
    }

    @Test
    fun `current null and non-empty visible returns first`() {
        pickNextMode(visible = listOf(off, tx, ad), current = null, pending = null) shouldBe off
    }

    @Test
    fun `wraps around at end of list`() {
        pickNextMode(visible = listOf(off, tx, ad), current = ad, pending = null) shouldBe off
    }

    @Test
    fun `advances through middle of list`() {
        pickNextMode(visible = listOf(off, tx, ad), current = tx, pending = null) shouldBe ad
    }

    @Test
    fun `pending wins over current when pending is in visible`() {
        // current=off, pending=tx → next is ad (anchor on pending so rapid taps walk forward)
        pickNextMode(visible = listOf(off, tx, ad), current = off, pending = tx) shouldBe ad
    }

    @Test
    fun `pending falls through to current when pending was filtered out`() {
        // pending=off but visible no longer contains off → fall through to current=tx → next is ad
        pickNextMode(visible = listOf(tx, ad), current = tx, pending = off) shouldBe ad
    }

    @Test
    fun `current not in visible falls through to first`() {
        // current=on but visible doesn't contain it → start at first (off)
        pickNextMode(visible = listOf(off, tx, ad), current = on, pending = null) shouldBe off
    }

    @Test
    fun `pending not visible and current null falls back to first`() {
        pickNextMode(visible = listOf(off, tx, ad), current = null, pending = on) shouldBe off
    }
}
