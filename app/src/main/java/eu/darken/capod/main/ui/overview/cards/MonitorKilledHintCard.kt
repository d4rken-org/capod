package eu.darken.capod.main.ui.overview.cards

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.BatterySaver
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.capod.R
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper

@Composable
fun MonitorKilledHintCard(
    showAutostartAction: Boolean,
    onShowInstructions: () -> Unit,
    onAutostartSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.TwoTone.BatterySaver,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(
                    text = stringResource(R.string.overview_os_kill_hint_label),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.overview_os_kill_hint_description),
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Stacked instead of a single Row — up to three actions don't fit on narrow screens,
            // especially localized or at increased font scale.
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End,
            ) {
                Button(onClick = onShowInstructions) {
                    Text(text = stringResource(R.string.overview_os_kill_hint_instructions_action))
                }

                if (showAutostartAction) {
                    OutlinedButton(onClick = onAutostartSettings) {
                        Text(text = stringResource(R.string.overview_os_kill_hint_autostart_action))
                    }
                }

                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.overview_os_kill_hint_dismiss_action))
                }
            }
        }
    }
}

@Preview2
@Composable
private fun MonitorKilledHintCardPreview() = PreviewWrapper {
    MonitorKilledHintCard(
        showAutostartAction = true,
        onShowInstructions = {},
        onAutostartSettings = {},
        onDismiss = {},
    )
}
