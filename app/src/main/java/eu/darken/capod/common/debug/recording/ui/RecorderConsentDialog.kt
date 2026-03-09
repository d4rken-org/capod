package eu.darken.capod.common.debug.recording.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.capod.R

@Composable
fun RecorderConsentDialog(
    onStartRecord: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.support_debuglog_label)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.settings_debuglog_explanation))
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        onOpenPrivacyPolicy()
                        onDismiss()
                    },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(text = stringResource(R.string.settings_privacy_policy_label))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onStartRecord()
            }) {
                Text(text = stringResource(R.string.debug_debuglog_record_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.general_cancel_action))
            }
        },
    )
}
