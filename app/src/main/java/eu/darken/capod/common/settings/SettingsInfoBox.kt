package eu.darken.capod.common.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper

enum class InfoBoxType { INFO, WARNING }

@Composable
fun SettingsInfoBox(
    text: String,
    modifier: Modifier = Modifier,
    type: InfoBoxType = InfoBoxType.INFO,
    action: @Composable (() -> Unit)? = null,
) {
    val containerColor = when (type) {
        InfoBoxType.INFO -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        InfoBoxType.WARNING -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
    }
    val iconTint = when (type) {
        InfoBoxType.INFO -> MaterialTheme.colorScheme.primary
        InfoBoxType.WARNING -> MaterialTheme.colorScheme.tertiary
    }
    val icon = when (type) {
        InfoBoxType.INFO -> Icons.Outlined.Info
        InfoBoxType.WARNING -> Icons.Outlined.Warning
    }

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = if (action != null) 8.dp else 16.dp),
            verticalAlignment = if (action != null) Alignment.Top else Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier
                    .padding(end = 10.dp)
                    .size(20.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = if (action != null) Alignment.End else Alignment.Start,
            ) {
                Text(
                    text = text,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                )
                if (action != null) {
                    action()
                }
            }
        }
    }
}

@Preview2
@Composable
private fun SettingsInfoBoxPreview() = PreviewWrapper {
    SettingsInfoBox(
        text = "If ear detection only works for one pod, this is an Apple limitation. Only the \"primary pod\" (used for microphone) is detected.",
    )
}

@Preview2
@Composable
private fun SettingsInfoBoxWarningPreview() = PreviewWrapper {
    SettingsInfoBox(
        text = "This feature requires the monitor to be running.",
        type = InfoBoxType.WARNING,
    )
}

@Preview2
@Composable
private fun SettingsInfoBoxWarningWithActionPreview() = PreviewWrapper {
    SettingsInfoBox(
        text = "Popups require the monitor to be running. Your monitor mode is set to \"When app is open\", which stops the monitor when you leave the app.",
        type = InfoBoxType.WARNING,
        action = {
            TextButton(onClick = {}) {
                Text("Fix it")
            }
        },
    )
}
