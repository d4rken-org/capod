package eu.darken.capod.common.upgrade.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

/**
 * The title template lets translators own word order and punctuation, so the styled qualifier has
 * to land on the right offsets wherever they put it. Assertions are on span boundaries, not just on
 * the concatenated text: the Estonian defect this replaces rendered the correct characters with the
 * highlight sitting on the wrong word.
 */
class TitleTemplateSpliceTest : BaseTest() {

    private val qualifierColor = Color.Red

    private val name = AnnotatedString("CAPod")

    private val qualifier: AnnotatedString = buildAnnotatedString {
        pushStyle(SpanStyle(color = qualifierColor))
        append("Pro")
        pop()
    }

    private fun template(pattern: String) = pattern
        .replace("%1\$s", BRAND_TITLE_MARKER)
        .replace("%2\$s", BRAND_QUALIFIER_MARKER)

    @Test
    fun `the default order highlights the trailing qualifier`() {
        val result = spliceTitleTemplate(template("%1\$s %2\$s"), name, qualifier)

        result.text shouldBe "CAPod Pro"
        result.spanStyles.size shouldBe 1
        result.spanStyles.single().item.color shouldBe qualifierColor
        result.spanStyles.single().start shouldBe 6
        result.spanStyles.single().end shouldBe 9
        result.text.substring(6, 9) shouldBe "Pro"
    }

    // Estonian: "Tasuline CAPod". The old split-on-space code passed its two-token guard here and
    // then styled the SECOND token, highlighting the brand instead of the tier.
    @Test
    fun `a reordered template highlights the leading qualifier`() {
        val result = spliceTitleTemplate(template("%2\$s %1\$s"), name, qualifier)

        result.text shouldBe "Pro CAPod"
        result.spanStyles.size shouldBe 1
        result.spanStyles.single().start shouldBe 0
        result.spanStyles.single().end shouldBe 3
        result.text.substring(0, 3) shouldBe "Pro"
    }

    @Test
    fun `a custom separator shifts the qualifier without entering the span`() {
        val result = spliceTitleTemplate(template("%1\$s – %2\$s"), name, qualifier)

        result.text shouldBe "CAPod – Pro"
        result.spanStyles.size shouldBe 1
        result.spanStyles.single().start shouldBe 8
        result.spanStyles.single().end shouldBe 11
        result.text.substring(8, 11) shouldBe "Pro"
    }

    // Arabic: "كابود – النسخة الاحترافية". Four space-separated tokens, so the old code's guard
    // failed and dropped the branding entirely rather than mis-styling it.
    @Test
    fun `a multi-word qualifier is highlighted whole`() {
        val multiWord = buildAnnotatedString {
            pushStyle(SpanStyle(color = qualifierColor))
            append("النسخة الاحترافية")
            pop()
        }

        val result = spliceTitleTemplate(template("%1\$s – %2\$s"), AnnotatedString("كابود"), multiWord)

        result.text shouldBe "كابود – النسخة الاحترافية"
        result.spanStyles.size shouldBe 1
        result.spanStyles.single().start shouldBe 8
        result.spanStyles.single().end shouldBe 25
        result.text.substring(8, 25) shouldBe "النسخة الاحترافية"
    }

    // Span offsets are UTF-16 indices, so a supplementary character ahead of a slot shifts it by
    // two. Pins that the splice arithmetic counts code units and not code points.
    @Test
    fun `a supplementary character before the slots shifts the span by two`() {
        val result = spliceTitleTemplate(template("🎧 %1\$s %2\$s"), name, qualifier)

        result.text shouldBe "🎧 CAPod Pro"
        result.spanStyles.size shouldBe 1
        result.spanStyles.single().start shouldBe 9
        result.spanStyles.single().end shouldBe 12
        result.text.substring(9, 12) shouldBe "Pro"
    }

    @Test
    fun `a duplicated name marker falls back to the complete default title`() {
        val result = spliceTitleTemplate(
            "$BRAND_TITLE_MARKER $BRAND_TITLE_MARKER $BRAND_QUALIFIER_MARKER",
            name,
            qualifier,
        )

        result.text shouldBe "CAPod Pro"
        result.spanStyles.size shouldBe 1
        result.spanStyles.single().start shouldBe 6
        result.spanStyles.single().end shouldBe 9
    }

    @Test
    fun `a duplicated qualifier marker falls back to the complete default title`() {
        val result = spliceTitleTemplate(
            "$BRAND_TITLE_MARKER $BRAND_QUALIFIER_MARKER $BRAND_QUALIFIER_MARKER",
            name,
            qualifier,
        )

        result.text shouldBe "CAPod Pro"
        result.spanStyles.size shouldBe 1
        result.spanStyles.single().start shouldBe 6
        result.spanStyles.single().end shouldBe 9
    }

    @Test
    fun `a missing name marker falls back rather than rendering the qualifier alone`() {
        val result = spliceTitleTemplate("Get $BRAND_QUALIFIER_MARKER", name, qualifier)

        result.text shouldBe "CAPod Pro"
        result.spanStyles.size shouldBe 1
        result.spanStyles.single().start shouldBe 6
        result.spanStyles.single().end shouldBe 9
    }

    @Test
    fun `a missing qualifier marker falls back rather than dropping the tier`() {
        val result = spliceTitleTemplate("Get $BRAND_TITLE_MARKER", name, qualifier)

        result.text shouldBe "CAPod Pro"
        result.spanStyles.size shouldBe 1
        result.spanStyles.single().start shouldBe 6
        result.spanStyles.single().end shouldBe 9
    }

    @Test
    fun `a template with neither marker falls back to the complete default title`() {
        val result = spliceTitleTemplate("CAPod Pro", name, qualifier)

        result.text shouldBe "CAPod Pro"
        result.spanStyles.size shouldBe 1
        result.spanStyles.single().item.color shouldBe qualifierColor
        result.spanStyles.single().start shouldBe 6
        result.spanStyles.single().end shouldBe 9
    }
}
