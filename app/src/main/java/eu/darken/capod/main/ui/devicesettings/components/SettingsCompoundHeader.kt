package eu.darken.capod.main.ui.devicesettings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Headphones
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper

@Composable
internal fun SettingsCompoundHeader(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    enabled: Boolean,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.6f else 0.3f),
            modifier = Modifier.padding(end = 16.dp),
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.5f),
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.5f),
                )
            }
        }
    }
}

@Preview2
@Composable
private fun SettingsCompoundHeaderPreview() = PreviewWrapper {
    SettingsCompoundHeader(
        icon = Icons.TwoTone.Headphones,
        title = "Noise Control",
        subtitle = "Switch between Off, On, Transparency, Adaptive",
        enabled = true,
    )
}

@Preview2
@Composable
private fun SettingsCompoundHeaderDisabledPreview() = PreviewWrapper {
    SettingsCompoundHeader(
        icon = Icons.TwoTone.Headphones,
        title = "Noise Control",
        subtitle = null,
        enabled = false,
    )
}
