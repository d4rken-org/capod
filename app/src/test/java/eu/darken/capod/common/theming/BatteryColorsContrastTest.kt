package eu.darken.capod.common.theming

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import io.kotest.assertions.withClue
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

/**
 * The overview's battery gauges sit on a `Surface(tonalElevation = 4.dp)` inside an `ElevatedCard`,
 * and a card without live data is drawn at `alpha(0.7f)`. Both variants are measured here: checking
 * a token against the raw `surface` colour would pass values that are unreadable on screen.
 *
 * Material You is dynamic and can't be enumerated, so the tokens are fixed and verified against the
 * full spread of the bundled palettes instead.
 */
class BatteryColorsContrastTest : BaseTest() {

    private val gaugeElevation = 4.dp
    private val notLiveAlpha = 0.7f

    /** WCAG 2.x minimum for a graphical object such as a gauge fill. */
    private val fillMinimum = 3.0

    /** WCAG 2.x minimum for text. */
    private val textMinimum = 4.5

    private data class Palette(
        val name: String,
        val scheme: ColorScheme,
        val tokens: BatteryColors,
    ) {
        /** Container colour of the ElevatedCard the gauges are drawn in. */
        val cardBackground: Color get() = scheme.surfaceContainerLow
    }

    private val palettes: List<Palette> = buildList {
        ThemeColor.entries.forEach { color ->
            ThemeStyle.entries.filter { it != ThemeStyle.MATERIAL_YOU }.forEach { style ->
                add(
                    Palette(
                        name = "$color/$style/light",
                        scheme = ThemeColorProvider.getLightColorScheme(color, style),
                        tokens = BatteryColors.Light,
                    )
                )
                add(
                    Palette(
                        name = "$color/$style/dark",
                        scheme = ThemeColorProvider.getDarkColorScheme(color, style),
                        tokens = BatteryColors.Dark,
                    )
                )
            }
        }
    }

    private fun contrast(a: Color, b: Color): Double {
        val brighter = maxOf(a.luminance(), b.luminance()).toDouble()
        val darker = minOf(a.luminance(), b.luminance()).toDouble()
        return (brighter + 0.05) / (darker + 0.05)
    }

    private fun Color.dimmed(over: Color): Color = copy(alpha = notLiveAlpha).compositeOver(over)

    private fun Palette.assertContrast(token: Color, against: Color, minimum: Double, what: String) {
        withClue("$what, $name") {
            contrast(token, against) shouldBeGreaterThanOrEqual minimum
        }
        withClue("$what, $name, card without live data") {
            contrast(token.dimmed(cardBackground), against.dimmed(cardBackground)) shouldBeGreaterThanOrEqual minimum
        }
    }

    @Test
    fun `every bundled palette is covered`() {
        palettes.size shouldBe 18
    }

    @Test
    fun `the warn fill stands out from the gauge track`() {
        palettes.forEach {
            it.assertContrast(
                token = it.tokens.warnFill,
                against = it.scheme.surfaceVariant,
                minimum = fillMinimum,
                what = "warn fill",
            )
        }
    }

    @Test
    fun `the warn text is readable on the gauge surface`() {
        palettes.forEach {
            it.assertContrast(
                token = it.tokens.warnText,
                against = it.scheme.surfaceColorAtElevation(gaugeElevation),
                minimum = textMinimum,
                what = "warn text",
            )
        }
    }

    @Test
    fun `the positive text is readable on the gauge surface`() {
        palettes.forEach {
            it.assertContrast(
                token = it.tokens.positiveText,
                against = it.scheme.surfaceColorAtElevation(gaugeElevation),
                minimum = textMinimum,
                what = "positive text",
            )
        }
    }
}
