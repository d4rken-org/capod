package eu.darken.capod.main.ui.devicesettings.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import eu.darken.capod.R
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper

@Composable
internal fun SystemRenameUnavailableDialog(
    onOpenBluetoothSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.device_settings_rename_system_unavailable_title)) },
        text = { Text(stringResource(R.string.device_settings_rename_system_unavailable)) },
        confirmButton = {
            TextButton(onClick = onOpenBluetoothSettings) {
                Text(stringResource(R.string.device_settings_rename_system_unavailable_bt_settings_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        },
    )
}

@Preview2
@Composable
private fun SystemRenameUnavailableDialogPreview() = PreviewWrapper {
    SystemRenameUnavailableDialog(
        onOpenBluetoothSettings = {},
        onDismiss = {},
    )
}
