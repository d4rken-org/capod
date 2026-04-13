package eu.darken.capod.common.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Notifications
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper

@Composable
fun SettingsSection(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column {
            if (title != null) {
                SettingsCategoryHeader(text = title)
            }
            content()
        }
    }
}

@Preview2
@Composable
private fun SettingsSectionPreview() = PreviewWrapper {
    SettingsSection(title = "Reactions") {
        SettingsSwitchItem(
            icon = Icons.TwoTone.Notifications,
            title = "Auto Play",
            subtitle = "Resume playback when AirPods are inserted",
            checked = true,
            onCheckedChange = {},
        )
        SettingsSwitchItem(
            icon = Icons.TwoTone.Notifications,
            title = "Auto Pause",
            subtitle = "Pause when AirPods are removed",
            checked = false,
            onCheckedChange = {},
        )
    }
}
