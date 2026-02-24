package eu.darken.capod.common.theming

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun CapodTheme(
    state: ThemeState = ThemeState(),
    content: @Composable () -> Unit,
) {
    val darkTheme = when (state.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val dynamicColors = state.style == ThemeStyle.MATERIAL_YOU && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = if (dynamicColors) {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (darkTheme) {
            ThemeColorProvider.getDarkColorScheme(state.color, state.style)
        } else {
            ThemeColorProvider.getLightColorScheme(state.color, state.style)
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
