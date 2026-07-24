package eu.darken.capod.upgrade.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import eu.darken.capod.R

// Described restore section, shared by all restore audiences (copy and emphasis differ, wiring
// doesn't). `enabled` covers the settled/verification gating; `restoreInProgress` only drives the
// spinner — a button that looks enabled while the ViewModel silently rejects the tap is worse than
// a disabled one. Deliberately NO contact-support action here: escalation is offered only after a
// restore came up empty (RestoreFailedDialog), so self-service gets its chance first.
@Composable
internal fun UpgradeRestoreSection(
    title: String,
    body: String,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
    restoreInProgress: Boolean = false,
    enabled: Boolean = true,
    emphasized: Boolean = false,
    restoreTag: String = UpgradeScreenTags.RESTORE_BUTTON,
) {
    UpgradeSectionCard(
        title = title,
        icon = Icons.TwoTone.Restore,
        modifier = modifier,
        colors = if (emphasized) {
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        } else {
            null
        },
    ) {
        if (emphasized) {
            // The tinted container brings its own content color; the muted body tone is for
            // neutral surface cards only.
            Text(text = body, style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = onRestore,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(restoreTag),
            ) {
                BusyButtonLabel(
                    busy = restoreInProgress,
                    text = stringResource(R.string.upgrade_screen_restore_purchase_action),
                )
            }
        } else {
            UpgradeSectionBody(text = body)
            OutlinedButton(
                onClick = onRestore,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(restoreTag),
            ) {
                BusyButtonLabel(
                    busy = restoreInProgress,
                    text = stringResource(R.string.upgrade_screen_restore_purchase_action),
                )
            }
        }
    }
}

// The only contact-support surface on the screen: it leads with the just-happened live Play check
// (RestoreFailed also fires on timeout, so the copy is hedged), then self-service hints, then the
// escalation. Dismiss uses the generic cancel action (capod has no dedicated dismiss string).
@Composable
internal fun RestoreFailedDialog(
    onDismiss: () -> Unit,
    onContactSupport: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.testTag(UpgradeScreenTags.DIALOG_RESTORE_FAILED),
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.upgrade_screen_restore_purchase_action)) },
        text = {
            Text(
                text = listOf(
                    stringResource(R.string.upgrade_screen_restore_checked_message),
                    stringResource(R.string.upgrade_screen_restore_multiaccount_hint),
                    stringResource(R.string.upgrade_screen_restore_sync_patience_hint),
                    stringResource(R.string.upgrade_screen_restore_contact_hint),
                ).joinToString("\n\n")
            )
        },
        confirmButton = {
            TextButton(
                onClick = onContactSupport,
                modifier = Modifier.testTag(UpgradeScreenTags.CONTACT_SUPPORT_BUTTON),
            ) {
                Text(text = stringResource(R.string.upgrade_screen_contact_support_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.general_cancel_action))
            }
        },
    )
}
