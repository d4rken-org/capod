package eu.darken.capod.common.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper

@Composable
fun SettingsSliderItem(
    icon: ImageVector,
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    valueLabel: ((Float) -> String)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SettingsBaseItem(
            icon = icon,
            title = title,
            subtitle = subtitle,
            onClick = {},
            enabled = enabled,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 56.dp)
                .padding(bottom = 8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Slider(
                value = value,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange = valueRange,
                steps = steps,
                enabled = enabled,
                modifier = Modifier.weight(1f),
            )
            if (valueLabel != null) {
                Text(
                    text = valueLabel(value),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.6f else 0.3f),
                    modifier = Modifier.width(42.dp),
                )
            }
        }
    }
}

@Preview2
@Composable
private fun SettingsSliderItemPreview() = PreviewWrapper {
    SettingsSliderItem(
        icon = Icons.AutoMirrored.TwoTone.VolumeUp,
        title = "Volume",
        subtitle = "Adjust volume level",
        value = 0.6f,
        onValueChange = {},
        valueLabel = { "${(it * 100).toInt()}%" },
    )
}
