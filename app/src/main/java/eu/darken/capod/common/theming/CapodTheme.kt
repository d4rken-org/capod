package eu.darken.capod.common.theming

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF3F7AFF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDAE2FF),
    onPrimaryContainer = Color(0xFF00174B),
    secondary = Color(0xFF715C00),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFE16C),
    onSecondaryContainer = Color(0xFF231B00),
    tertiary = Color(0xFF006D39),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF5CFFA0),
    onTertiaryContainer = Color(0xFF00210D),
    error = Color(0xFFBA1B1B),
    errorContainer = Color(0xFFFFDAD4),
    onError = Color(0xFFFFFFFF),
    onErrorContainer = Color(0xFF410001),
    background = Color(0xFFFEFBFF),
    onBackground = Color(0xFF1B1B1F),
    surface = Color(0xFFFEFBFF),
    onSurface = Color(0xFF1B1B1F),
    surfaceVariant = Color(0xFFE2E2EC),
    onSurfaceVariant = Color(0xFF44464E),
    outline = Color(0xFF75767F),
    inverseSurface = Color(0xFF303033),
    inverseOnSurface = Color(0xFFF2F0F5),
    inversePrimary = Color(0xFFB1C5FF),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF3F7AFF),
    onPrimary = Color(0xFF002A78),
    primaryContainer = Color(0xFF003EA7),
    onPrimaryContainer = Color(0xFFDAE2FF),
    secondary = Color(0xFFE9C426),
    onSecondary = Color(0xFF3B2F00),
    secondaryContainer = Color(0xFF564500),
    onSecondaryContainer = Color(0xFFFFE16C),
    tertiary = Color(0xFF33E286),
    onTertiary = Color(0xFF00391B),
    tertiaryContainer = Color(0xFF005229),
    onTertiaryContainer = Color(0xFF5CFFA0),
    error = Color(0xFFFFB4A9),
    errorContainer = Color(0xFF930006),
    onError = Color(0xFF680003),
    onErrorContainer = Color(0xFFFFDAD4),
    background = Color(0xFF1B1B1F),
    onBackground = Color(0xFFE3E1E6),
    surface = Color(0xFF1B1B1F),
    onSurface = Color(0xFFE3E1E6),
    surfaceVariant = Color(0xFF44464E),
    onSurfaceVariant = Color(0xFFC6C6D0),
    outline = Color(0xFF8F909A),
    inverseSurface = Color(0xFFE3E1E6),
    inverseOnSurface = Color(0xFF1B1B1F),
    inversePrimary = Color(0xFF0054D9),
)

@Composable
fun CapodTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
