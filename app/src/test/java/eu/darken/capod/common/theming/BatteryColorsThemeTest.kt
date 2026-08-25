package eu.darken.capod.common.theming

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import eu.darken.capod.monitor.core.battery.BatteryTier
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

/**
 * Colour is not part of the semantics tree and `captureToImage()` does not work under Robolectric,
 * so what a rendered card looks like is checked in two halves: this class pins the colour each tier
 * resolves to, and the card tests pin which tier each slot reports.
 */
class BatteryColorsThemeTest : BaseComposeRobolectricTest() {

    private class Resolved {
        var warnFill: Color = Color.Unspecified
        var criticalFill: Color = Color.Unspecified
        var goodFill: Color = Color.Unspecified
        var unknownFill: Color = Color.Unspecified
        var warnText: Color? = null
        var criticalText: Color? = null
        var goodText: Color? = null
        var unknownText: Color? = null
        var error: Color = Color.Unspecified
        var primary: Color = Color.Unspecified
        var surfaceVariant: Color = Color.Unspecified
    }

    private fun resolve(mode: ThemeMode): Resolved {
        val resolved = Resolved()
        composeRule.setContent {
            CapodTheme(state = ThemeState(mode = mode)) {
                resolved.warnFill = BatteryTier.WARN.fillColor()
                resolved.criticalFill = BatteryTier.CRITICAL.fillColor()
                resolved.goodFill = BatteryTier.GOOD.fillColor()
                resolved.unknownFill = BatteryTier.UNKNOWN.fillColor()
                resolved.warnText = BatteryTier.WARN.textColorOrNull()
                resolved.criticalText = BatteryTier.CRITICAL.textColorOrNull()
                resolved.goodText = BatteryTier.GOOD.textColorOrNull()
                resolved.unknownText = BatteryTier.UNKNOWN.textColorOrNull()
                resolved.error = MaterialTheme.colorScheme.error
                resolved.primary = MaterialTheme.colorScheme.primary
                resolved.surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
            }
        }
        composeRule.waitForIdle()
        return resolved
    }

    @Test
    fun `the light theme uses the light tokens`() {
        val resolved = resolve(ThemeMode.LIGHT)

        resolved.warnFill shouldBe BatteryColors.Light.warnFill
        resolved.warnText shouldBe BatteryColors.Light.warnText
    }

    @Test
    fun `an in-app dark override uses the dark tokens`() {
        // The host system is in light mode here, which is exactly the case a plain
        // isSystemInDarkTheme() read inside the mapper would get wrong.
        val resolved = resolve(ThemeMode.DARK)

        resolved.warnFill shouldBe BatteryColors.Dark.warnFill
        resolved.warnText shouldBe BatteryColors.Dark.warnText
    }

    @Test
    fun `the other tiers stay on the palette`() {
        val resolved = resolve(ThemeMode.LIGHT)

        resolved.criticalFill shouldBe resolved.error
        resolved.criticalText shouldBe resolved.error
        resolved.goodFill shouldBe resolved.primary
        resolved.unknownFill shouldBe resolved.surfaceVariant
    }

    @Test
    fun `only a warning or worse claims the text colour`() {
        val resolved = resolve(ThemeMode.LIGHT)

        resolved.goodText.shouldBeNull()
        resolved.unknownText.shouldBeNull()
    }
}
