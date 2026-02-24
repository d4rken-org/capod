package eu.darken.capod.common.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun SettingsPreferenceItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    enabled: Boolean = true,
) {
    val contentAlpha = if (enabled) 1f else 0.5f

    SettingsBaseItem(
        icon = icon,
        title = title,
        onClick = onClick,
        modifier = modifier,
        subtitle = subtitle,
        enabled = enabled,
        trailingContent = if (value != null) {
            {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f * contentAlpha),
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
        } else null
    )
}
