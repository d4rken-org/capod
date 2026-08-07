package eu.darken.capod.common.upgrade.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.test.core.app.ApplicationProvider
import eu.darken.capod.R
import eu.darken.capod.common.compose.PreviewWrapper
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

/**
 * Resolves the real flavor resources rather than a sample pattern, so this also proves the two
 * markers survive Android's format path and never reach the user.
 *
 * Flavor-agnostic on purpose: it asserts against whatever this variant's qualifier resource says
 * ("Pro" on GPLAY, "FOSS" on FOSS) so the one test guards both. The resources are flavor-owned, so
 * a variant that compiles proves nothing about the other.
 */
class BrandTitleTest : BaseComposeRobolectricTest() {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val name: String
        get() = context.getString(R.string.app_name)

    private val qualifier: String
        get() = context.getString(R.string.upgrade_badge_label)

    private val composed: String
        get() = context.getString(R.string.app_name_upgraded_template, name, qualifier)

    private fun capture(block: @Composable () -> AnnotatedString): AnnotatedString {
        lateinit var captured: AnnotatedString
        composeRule.setContent {
            PreviewWrapper { captured = block() }
        }
        composeRule.waitForIdle()
        return captured
    }

    @Test
    fun `without the qualifier the title is the bare app name`() {
        val result = capture { brandTitle(includeQualifier = false, highlightQualifier = false) }

        result.text shouldBe name
        result.spanStyles.size shouldBe 0
    }

    // The regression guard for the two-flag split: the qualifier is present but NOT colored.
    // Collapsing the flags drops it; highlighting on `includeQualifier` alone colors it. Both would
    // still produce plausible-looking text, so the span count is the assertion that matters.
    @Test
    fun `an included but unhighlighted qualifier is present and carries no span`() {
        val result = capture { brandTitle(includeQualifier = true, highlightQualifier = false) }

        result.text shouldBe composed
        result.text.contains(qualifier) shouldBe true
        result.spanStyles.size shouldBe 0
    }

    @Test
    fun `a highlighted qualifier carries exactly one span covering the qualifier only`() {
        val result = capture { brandTitle(includeQualifier = true, highlightQualifier = true) }

        result.text shouldBe composed
        result.spanStyles.size shouldBe 1
        val span = result.spanStyles.single()
        // Not just "a span exists" — the bug class this replaces put the highlight on the app name
        // while rendering perfectly correct text.
        result.text.substring(span.start, span.end) shouldBe qualifier
    }

    @Test
    fun `the highlight defaults to the upgraded brand color`() {
        val result = capture { brandTitle(includeQualifier = true, highlightQualifier = true) }

        result.spanStyles.single().item.color shouldBe Color(context.getColor(R.color.brand_tertiary))
    }

    // The toolbar tints by flavor — FOSS on brand_secondary, Pro on brand_tertiary — so the color
    // is a parameter rather than a constant. Without this, hardcoding the default back into
    // brandTitle would keep every other assertion green while the FOSS toolbar lost its tint.
    @Test
    fun `a caller-supplied highlight color is the one applied`() {
        val custom = Color(context.getColor(R.color.brand_secondary))

        val result = capture {
            brandTitle(includeQualifier = true, highlightQualifier = true, highlightColor = custom)
        }

        result.spanStyles.single().item.color shouldBe custom
        result.text.substring(
            result.spanStyles.single().start,
            result.spanStyles.single().end,
        ) shouldBe qualifier
    }

    // The markers are injected as format arguments, so a template or formatter that mangled them
    // would leak U+FFFC / U+FFF9 into the toolbar.
    @Test
    fun `neither splice marker survives into the rendered title`() {
        val result = capture { brandTitle(includeQualifier = true, highlightQualifier = true) }

        result.text shouldNotContain BRAND_TITLE_MARKER
        result.text shouldNotContain BRAND_QUALIFIER_MARKER
    }

    @Test
    fun `the string form matches the annotated form`() {
        val result = capture { AnnotatedString(brandTitleText(includeQualifier = true)) }

        result.text shouldBe composed
    }

    // upgradeScreenTitle is the thin wrapper both upgrade screens title themselves with: it must
    // keep naming the flavor even while the screen shows the free state.
    @Test
    fun `the upgrade screen title keeps the qualifier when not upgraded`() {
        val result = capture { upgradeScreenTitle(upgraded = false) }

        result.text shouldBe composed
        result.spanStyles.size shouldBe 0
    }
}
