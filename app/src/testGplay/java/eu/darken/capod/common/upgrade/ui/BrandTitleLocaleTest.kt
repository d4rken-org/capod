package eu.darken.capod.common.upgrade.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.test.core.app.ApplicationProvider
import eu.darken.capod.R
import eu.darken.capod.common.compose.PreviewWrapper
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.robolectric.annotation.Config
import testhelpers.compose.BaseComposeRobolectricTest

/**
 * The two locales that broke the old split-on-space title, resolved through the real translated
 * resources rather than a sample pattern.
 *
 * Both assert on the span boundary, not on text alone: Estonian rendered the correct characters
 * with the highlight on the wrong word, so a text-only assertion would have passed throughout.
 */
abstract class BrandTitleLocaleTest : BaseComposeRobolectricTest() {

    protected val context: Context
        get() = ApplicationProvider.getApplicationContext()

    protected val name: String
        get() = context.getString(R.string.app_name)

    protected val qualifier: String
        get() = context.getString(R.string.upgrade_badge_label)

    protected val composed: String
        get() = context.getString(R.string.app_name_upgraded_template, name, qualifier)

    protected fun capture(block: @Composable () -> AnnotatedString): AnnotatedString {
        lateinit var captured: AnnotatedString
        composeRule.setContent {
            PreviewWrapper { captured = block() }
        }
        composeRule.waitForIdle()
        return captured
    }

    /**
     * Arabic composes the title with an en-dash separator and a two-word qualifier
     * ("كابود – النسخة الاحترافية"). The old code split on spaces and bailed out unless it saw
     * exactly two tokens, so four tokens meant the Pro branding vanished from the toolbar entirely
     * and the upgrade screen showed the name uncolored.
     */
    @Config(qualifiers = "ar")
    class Arabic : BrandTitleLocaleTest() {

        @Test
        fun `the multi-word qualifier is present and highlighted whole`() {
            val result = capture { upgradeScreenTitle(upgraded = true) }

            result.text shouldBe composed
            // The qualifier is genuinely multi-word here — that is what defeated the token count.
            qualifier.contains(" ") shouldBe true
            result.spanStyles.size shouldBe 1
            val span = result.spanStyles.single()
            result.text.substring(span.start, span.end) shouldBe qualifier
        }

        @Test
        fun `the separator stays outside the highlight`() {
            val result = capture { upgradeScreenTitle(upgraded = true) }

            val span = result.spanStyles.single()
            // The name and its separator precede the qualifier and must not be colored.
            result.text.substring(0, span.start) shouldBe composed.removeSuffix(qualifier)
            result.text.substring(0, span.start).contains(name) shouldBe true
        }
    }

    /**
     * Estonian puts the qualifier FIRST ("Tasuline CAPod"). That splits to exactly two tokens, so
     * the old guard passed and then styled the second one — highlighting the brand and leaving the
     * tier plain. Silently backwards, with no fallback to catch it.
     */
    @Config(qualifiers = "et-rEE")
    class Estonian : BrandTitleLocaleTest() {

        @Test
        fun `the leading qualifier carries the highlight, not the app name`() {
            val result = capture { upgradeScreenTitle(upgraded = true) }

            result.text shouldBe composed
            result.spanStyles.size shouldBe 1
            val span = result.spanStyles.single()
            result.text.substring(span.start, span.end) shouldBe qualifier
        }

        @Test
        fun `the qualifier really does precede the app name in this locale`() {
            val result = capture { upgradeScreenTitle(upgraded = true) }

            // Pins the reordering itself: if the template ever regressed to the default order this
            // would still highlight the right word, so without this the locale's whole point is
            // untested.
            val span = result.spanStyles.single()
            span.start shouldBe 0
            result.text.indexOf(name) shouldBe qualifier.length + 1
        }
    }
}
