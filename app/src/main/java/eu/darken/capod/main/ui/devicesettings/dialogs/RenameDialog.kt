package eu.darken.capod.main.ui.devicesettings.dialogs

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import eu.darken.capod.R
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper

@Composable
internal fun RenameDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var textValue by remember { mutableStateOf(currentName) }
    val focusRequester = remember { FocusRequester() }

    // The decoder in DefaultAapDeviceProfile only round-trips printable ASCII (0x20..0x7E),
    // so even if the device accepts a UTF-8 name we can't display it back correctly. Accept any
    // input up to the 32-byte UX cap, but flag non-ASCII with an inline error so the user
    // understands why Rename is disabled.
    val hasInvalidAscii = textValue.any { it.code !in 0x20..0x7E }
    val isValid = textValue.isNotBlank() && !hasInvalidAscii
    val canConfirm = isValid && textValue != currentName

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.device_settings_rename_label)) },
        text = {
            OutlinedTextField(
                value = textValue,
                onValueChange = { newValue ->
                    // US_ASCII encoding maps non-ASCII chars to '?' (1 byte each), giving a
                    // stable upper bound equal to the UTF-16 char count. Keeps the cap the user
                    // sees consistent regardless of character content.
                    if (newValue.toByteArray(Charsets.US_ASCII).size <= 32) textValue = newValue
                },
                singleLine = true,
                label = { Text(stringResource(R.string.device_settings_rename_hint)) },
                isError = hasInvalidAscii,
                supportingText = if (hasInvalidAscii) {
                    { Text(stringResource(R.string.device_settings_rename_invalid_ascii)) }
                } else null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (canConfirm) onConfirm(textValue) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (canConfirm) onConfirm(textValue) },
                enabled = canConfirm,
            ) {
                Text(stringResource(R.string.device_settings_rename_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Preview2
@Composable
private fun RenameDialogPreview() = PreviewWrapper {
    RenameDialog(
        currentName = "AirPods Pro",
        onConfirm = {},
        onDismiss = {},
    )
}
