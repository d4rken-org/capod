package eu.darken.capod.common.upgrade.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

/**
 * The brand is spliced into the already-formatted translation, so the styled postfix has to land on
 * the right offsets no matter where the pattern put the placeholder.
 */
class BrandTitleSpliceTest : BaseTest() {

    private val brandColor = Color.Red

    // "CAPod Pro" with the postfix (6..9) colored, like upgradeScreenTitle(upgraded = true).
    private val brand: AnnotatedString = buildAnnotatedString {
        append("CAPod ")
        pushStyle(SpanStyle(color = brandColor))
        append("Pro")
        pop()
    }

    @Test fun `marker in the middle shifts the styled postfix by the prefix`() {
        val result = spliceBrandTitle("Get $BRAND_TITLE_MARKER", brand)

        result.text shouldBe "Get CAPod Pro"
        result.spanStyles.size shouldBe 1
        result.spanStyles.single().item.color shouldBe brandColor
        result.spanStyles.single().start shouldBe 10
        result.spanStyles.single().end shouldBe 13
        result.text.substring(10, 13) shouldBe "Pro"
    }

    @Test fun `marker at the start keeps the postfix offsets inside the brand`() {
        val result = spliceBrandTitle("$BRAND_TITLE_MARKER holen", brand)

        result.text shouldBe "CAPod Pro holen"
        result.spanStyles.size shouldBe 1
        result.spanStyles.single().start shouldBe 6
        result.spanStyles.single().end shouldBe 9
        result.text.substring(6, 9) shouldBe "Pro"
    }

    @Test fun `a duplicated marker renders the brand twice`() {
        val result = spliceBrandTitle("$BRAND_TITLE_MARKER und $BRAND_TITLE_MARKER", brand)

        result.text shouldBe "CAPod Pro und CAPod Pro"
        result.spanStyles.size shouldBe 2
        result.spanStyles[0].start shouldBe 6
        result.spanStyles[0].end shouldBe 9
        result.spanStyles[1].start shouldBe 20
        result.spanStyles[1].end shouldBe 23
        result.text.substring(20, 23) shouldBe "Pro"
    }

    @Test fun `a translation that lost the placeholder still shows the brand`() {
        val result = spliceBrandTitle("Get Pro", brand)

        result.text shouldBe "Get Pro CAPod Pro"
        result.spanStyles.size shouldBe 1
        result.spanStyles.single().item.color shouldBe brandColor
        result.spanStyles.single().start shouldBe 14
        result.spanStyles.single().end shouldBe 17
    }
}
