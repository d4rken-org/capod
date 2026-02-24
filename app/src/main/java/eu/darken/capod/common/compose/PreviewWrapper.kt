package eu.darken.capod.common.compose

import androidx.compose.runtime.Composable
import eu.darken.capod.common.theming.CapodTheme

@Composable
fun PreviewWrapper(content: @Composable () -> Unit) {
    CapodTheme {
        content()
    }
}
