package eu.darken.capod.common.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

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
    valueLabel: ((Float) -> String)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SettingsBaseItem(
            icon = icon,
            title = title,
            subtitle = subtitle,
            onClick = {},
            enabled = enabled,
            trailingContent = if (valueLabel != null) {
                {
                    Text(
                        text = valueLabel(value),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            } else null,
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 56.dp)
                .padding(bottom = 8.dp),
        )
    }
}
