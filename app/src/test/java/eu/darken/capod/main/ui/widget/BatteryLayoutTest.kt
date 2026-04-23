package eu.darken.capod.main.ui.widget

import eu.darken.capod.main.ui.widget.BatteryLayout.NARROW
import eu.darken.capod.main.ui.widget.BatteryLayout.TINY_COLUMN
import eu.darken.capod.main.ui.widget.BatteryLayout.WIDE
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class BatteryLayoutTest : BaseTest() {

    @Test fun `1 cell wide resolves to tiny column`() {
        BatteryLayout.forCells(1) shouldBe TINY_COLUMN
    }

    @Test fun `2 cells wide resolves to narrow`() {
        BatteryLayout.forCells(2) shouldBe NARROW
    }

    @Test fun `3 cells wide resolves to narrow`() {
        BatteryLayout.forCells(3) shouldBe NARROW
    }

    @Test fun `4 cells wide resolves to narrow`() {
        BatteryLayout.forCells(4) shouldBe NARROW
    }

    @Test fun `5 cells wide resolves to wide`() {
        BatteryLayout.forCells(5) shouldBe WIDE
    }

    @Test fun `8 cells wide resolves to wide`() {
        BatteryLayout.forCells(8) shouldBe WIDE
    }

    @Test fun `zero cells resolves to tiny column not thrown`() {
        BatteryLayout.forCells(0) shouldBe TINY_COLUMN
    }
}
