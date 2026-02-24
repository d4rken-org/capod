package eu.darken.capod.common.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper

@Composable
fun <T> SettingsListPreferenceItem(
    icon: ImageVector,
    title: String,
    entries: List<T>,
    selectedEntry: T,
    onEntrySelected: (T) -> Unit,
    entryLabel: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    var showDialog by remember { mutableStateOf(false) }

    SettingsBaseItem(
        icon = icon,
        title = title,
        subtitle = subtitle ?: entryLabel(selectedEntry),
        onClick = { if (enabled) showDialog = true },
        modifier = modifier,
        enabled = enabled,
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(text = title) },
            text = {
                Column(Modifier.selectableGroup()) {
                    entries.forEach { entry ->
                        val isSelected = entry == selectedEntry
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = isSelected,
                                    onClick = {
                                        onEntrySelected(entry)
                                        showDialog = false
                                    },
                                    role = Role.RadioButton,
                                )
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = null,
                            )
                            Text(
                                text = entryLabel(entry),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 16.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Preview2
@Composable
private fun SettingsListPreferenceItemPreview() = PreviewWrapper {
    SettingsListPreferenceItem(
        icon = Icons.TwoTone.Tune,
        title = "Mode",
        entries = listOf("Manual", "Automatic", "Always"),
        selectedEntry = "Automatic",
        onEntrySelected = {},
        entryLabel = { it },
    )
}
