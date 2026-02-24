package eu.darken.capod.common.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import eu.darken.capod.common.theming.CapodTheme

@Composable
fun PreviewWrapper(content: @Composable () -> Unit) {
    CapodTheme(dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            content()
        }
    }
}
