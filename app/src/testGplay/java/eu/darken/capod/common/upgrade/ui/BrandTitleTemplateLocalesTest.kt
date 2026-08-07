package eu.darken.capod.common.upgrade.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import eu.darken.capod.R
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.TestApplication
import java.util.Locale

/**
 * Sweeps every shipped locale through the real Android format path.
 *
 * A translated template is code the formatter executes, not inert text: a stray `%`, a `%3$s` or a
 * `%1$d` throws inside `getString` *before* the splice fallback can run, so no amount of defensive
 * splicing protects against it. This is the only place that failure mode is caught.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class BrandTitleTemplateLocalesTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun localized(tag: String): Context = context.createConfigurationContext(
        Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) },
    )

    private val locales: List<String>
        get() = context.assets.locales.filter { it.isNotBlank() }.sorted()

    // Mirrors brandTitle's composition without Compose, so all locales can be swept cheaply.
    private fun compose(ctx: Context): AnnotatedString {
        val qualifier = buildAnnotatedString {
            pushStyle(SpanStyle(color = Color.Red))
            append(ctx.getString(R.string.upgrade_badge_label))
            pop()
        }
        return spliceTitleTemplate(
            formatted = ctx.getString(
                R.string.app_name_upgraded_template,
                BRAND_TITLE_MARKER,
                BRAND_QUALIFIER_MARKER,
            ),
            name = AnnotatedString(ctx.getString(R.string.app_name)),
            qualifier = qualifier,
        )
    }

    // Guards the sweep itself: if locale enumeration ever silently returned one entry, every
    // assertion below would still pass while testing nothing.
    @Test
    fun `the locale sweep actually covers the shipped translations`() {
        locales shouldHaveAtLeastSize 60
    }

    // Asserted against the FORMATTER'S OUTPUT rather than against the template text, because the
    // format grammar is bigger than it looks: `%1$s %2$s %<s` reuses the previous argument and so
    // emits the qualifier twice, which no reasonable "does it contain %1$s and %2$s" check spots.
    // Each marker landing exactly once is precisely the condition spliceTitleTemplate requires, so
    // checking it here is both stronger and simpler than modelling the grammar.
    @Test
    fun `every locale template places each slot exactly once when formatted`() {
        val offenders = locales.mapNotNull { tag ->
            val formatted = localized(tag).getString(
                R.string.app_name_upgraded_template,
                BRAND_TITLE_MARKER,
                BRAND_QUALIFIER_MARKER,
            )
            val names = formatted.split(BRAND_TITLE_MARKER).size - 1
            val qualifiers = formatted.split(BRAND_QUALIFIER_MARKER).size - 1
            if (names == 1 && qualifiers == 1) {
                null
            } else {
                "$tag -> name x$names, qualifier x$qualifiers in '$formatted'"
            }
        }

        offenders shouldBe emptyList()
    }

    @Test
    fun `every locale resolves to a title that highlights exactly its qualifier`() {
        val offenders = locales.mapNotNull { tag ->
            val ctx = localized(tag)
            val name = ctx.getString(R.string.app_name)
            val qualifier = ctx.getString(R.string.upgrade_badge_label)
            // Throws here rather than failing an assertion if a template is malformed — which is
            // exactly the production failure being guarded against.
            val result = compose(ctx)

            val span = result.spanStyles.singleOrNull()
            // What Android itself produces for this template. Comparing against it is what proves
            // the splice REPRODUCED the translator's arrangement rather than quietly discarding a
            // damaged template and rebuilding the default — a fallback renders a plausible title
            // with exactly one correctly-styled qualifier, so every other assertion here passes
            // straight through it.
            val expected = ctx.getString(R.string.app_name_upgraded_template, name, qualifier)
            when {
                name.isBlank() || qualifier.isBlank() -> "$tag -> blank part"
                result.text.contains(BRAND_TITLE_MARKER) -> "$tag -> name marker leaked"
                result.text.contains(BRAND_QUALIFIER_MARKER) -> "$tag -> qualifier marker leaked"
                result.text != expected -> "$tag -> rendered '${result.text}', formatter says '$expected'"
                span == null -> "$tag -> expected one span, got ${result.spanStyles.size}"
                result.text.substring(span.start, span.end) != qualifier ->
                    "$tag -> span covers '${result.text.substring(span.start, span.end)}', want '$qualifier'"

                else -> null
            }
        }

        offenders shouldBe emptyList()
    }
}
