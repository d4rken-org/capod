package eu.darken.capod.common.theming

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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

    val context = LocalContext.current
    val colorScheme = remember(state, darkTheme, dynamicColors) {
        when {
            dynamicColors && darkTheme -> dynamicDarkColorScheme(context)
            dynamicColors && !darkTheme -> dynamicLightColorScheme(context)
            darkTheme -> ThemeColorProvider.getDarkColorScheme(state.color, state.style)
            else -> ThemeColorProvider.getLightColorScheme(state.color, state.style)
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
