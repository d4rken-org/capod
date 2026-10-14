package eu.darken.capod.main.ui.widget

import eu.darken.capod.main.ui.widget.BatteryLayout.NARROW
import eu.darken.capod.main.ui.widget.BatteryLayout.TINY_COLUMN
import eu.darken.capod.main.ui.widget.BatteryLayout.WIDE
import eu.darken.capod.main.ui.widget.LastKnownPlacement.APPEND_TO_LABEL
import eu.darken.capod.main.ui.widget.LastKnownPlacement.LABEL_SLOT
import eu.darken.capod.main.ui.widget.LastKnownPlacement.NONE
import eu.darken.capod.main.ui.widget.LastKnownPlacement.OWN_LINE
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class LastKnownPlacementTest : BaseTest() {

    private fun placement(
        hasLastKnown: Boolean = true,
        isDualPod: Boolean = true,
        layout: BatteryLayout = NARROW,
        labelShown: Boolean = true,
        heightDp: Float = 500f,
        fontScale: Float = 1.0f,
    ) = lastKnownPlacement(
        hasLastKnown = hasLastKnown,
        isDualPod = isDualPod,
        layout = layout,
        labelShown = labelShown,
        heightDp = heightDp,
        fontScale = fontScale,
    )

    @Test fun `no last-known value resolves to none`() {
        placement(hasLastKnown = false) shouldBe NONE
        placement(hasLastKnown = false, labelShown = false) shouldBe NONE
    }

    @Test fun `tiny column resolves to none`() {
        placement(layout = TINY_COLUMN) shouldBe NONE
        placement(layout = TINY_COLUMN, isDualPod = false) shouldBe NONE
        placement(layout = TINY_COLUMN, labelShown = false) shouldBe NONE
    }

    @Test fun `hidden label resolves to label slot regardless of height`() {
        placement(labelShown = false, heightDp = 500f) shouldBe LABEL_SLOT
        placement(labelShown = false, heightDp = 10f) shouldBe LABEL_SLOT
        placement(labelShown = false, layout = WIDE) shouldBe LABEL_SLOT
        placement(labelShown = false, isDualPod = false) shouldBe LABEL_SLOT
    }

    @Test fun `dual narrow threshold`() {
        placement(isDualPod = true, layout = NARROW, heightDp = 99.5f) shouldBe APPEND_TO_LABEL
        placement(isDualPod = true, layout = NARROW, heightDp = 100f) shouldBe OWN_LINE
    }

    @Test fun `dual wide threshold`() {
        placement(isDualPod = true, layout = WIDE, heightDp = 91.5f) shouldBe APPEND_TO_LABEL
        placement(isDualPod = true, layout = WIDE, heightDp = 92f) shouldBe OWN_LINE
    }

    @Test fun `single narrow threshold`() {
        placement(isDualPod = false, layout = NARROW, heightDp = 59.5f) shouldBe APPEND_TO_LABEL
        placement(isDualPod = false, layout = NARROW, heightDp = 60f) shouldBe OWN_LINE
    }

    @Test fun `single wide threshold`() {
        placement(isDualPod = false, layout = WIDE, heightDp = 59.5f) shouldBe APPEND_TO_LABEL
        placement(isDualPod = false, layout = WIDE, heightDp = 60f) shouldBe OWN_LINE
    }

    @Test fun `larger font scale raises the threshold`() {
        placement(isDualPod = true, layout = NARROW, heightDp = 100f, fontScale = 1.3f) shouldBe APPEND_TO_LABEL
        placement(isDualPod = true, layout = WIDE, heightDp = 92f, fontScale = 1.3f) shouldBe APPEND_TO_LABEL
        placement(isDualPod = false, layout = NARROW, heightDp = 60f, fontScale = 1.3f) shouldBe APPEND_TO_LABEL
        placement(isDualPod = false, layout = WIDE, heightDp = 60f, fontScale = 1.3f) shouldBe APPEND_TO_LABEL
    }
}
