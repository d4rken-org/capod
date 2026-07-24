package eu.darken.capod.common.compose

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class InsetsExtensionsTest : BaseTest() {

    @Test
    fun `plus - sums start-end padding under Ltr`() {
        val a = PaddingValues(start = 4.dp, top = 8.dp, end = 12.dp, bottom = 16.dp)
        val b = PaddingValues(start = 1.dp, top = 2.dp, end = 3.dp, bottom = 4.dp)
        val sum = a + b
        sum.calculateLeftPadding(LayoutDirection.Ltr) shouldBe 5.dp
        sum.calculateRightPadding(LayoutDirection.Ltr) shouldBe 15.dp
        sum.calculateTopPadding() shouldBe 10.dp
        sum.calculateBottomPadding() shouldBe 20.dp
    }

    @Test
    fun `plus - sums start-end padding under Rtl`() {
        val a = PaddingValues(start = 4.dp, top = 8.dp, end = 12.dp, bottom = 16.dp)
        val b = PaddingValues(start = 1.dp, top = 2.dp, end = 3.dp, bottom = 4.dp)
        val sum = a + b
        sum.calculateLeftPadding(LayoutDirection.Rtl) shouldBe 15.dp
        sum.calculateRightPadding(LayoutDirection.Rtl) shouldBe 5.dp
        sum.calculateTopPadding() shouldBe 10.dp
        sum.calculateBottomPadding() shouldBe 20.dp
    }
}
