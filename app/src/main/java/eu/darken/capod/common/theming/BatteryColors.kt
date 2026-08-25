package eu.darken.capod.common.theming

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import eu.darken.capod.monitor.core.battery.BatteryTier

/**
 * Battery colours that must not move with the palette. `colorScheme.tertiary` is whatever the seed
 * happens to produce (olive under the amber theme, teal under blue), so a level warning drawn from
 * it reads as decoration rather than as a warning.
 *
 * The values are picked against the composited backgrounds the overview actually draws on, see
 * `BatteryColorsContrastTest`. Critical stays on `colorScheme.error` and good on
 * `colorScheme.primary` — both already carry the meaning we want.
 */
@Immutable
data class BatteryColors(
    val warnFill: Color,
    val warnText: Color,
    val positiveText: Color,
) {
    companion object {
        val Light = BatteryColors(
            warnFill = Color(0xFF874400),
            warnText = Color(0xFF522300),
            positiveText = Color(0xFF003B0C),
        )
        val Dark = BatteryColors(
            warnFill = Color(0xFFFFA726),
            warnText = Color(0xFFFFCC80),
            positiveText = Color(0xFFA5D6A7),
        )
    }
}

/**
 * Provided by [CapodTheme] from the theme mode it already resolved. Reading
 * `isSystemInDarkTheme()` here instead would pick light tokens for an in-app dark override on a
 * light system.
 */
val LocalBatteryColors = staticCompositionLocalOf { BatteryColors.Light }

@Composable
fun BatteryTier.fillColor(): Color = when (this) {
    BatteryTier.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant
    BatteryTier.CRITICAL -> MaterialTheme.colorScheme.error
    BatteryTier.WARN -> LocalBatteryColors.current.warnFill
    BatteryTier.GOOD -> MaterialTheme.colorScheme.primary
}

/** Null where the level makes no claim, so callers keep their own default text colour. */
@Composable
fun BatteryTier.textColorOrNull(): Color? = when (this) {
    BatteryTier.CRITICAL -> MaterialTheme.colorScheme.error
    BatteryTier.WARN -> LocalBatteryColors.current.warnText
    BatteryTier.UNKNOWN, BatteryTier.GOOD -> null
}
