package eu.darken.capod.main.ui.devicesettings.cards

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.capod.R
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper

@Composable
internal fun NotConnectedCard(
    isNudgeAvailable: Boolean,
    isForceConnecting: Boolean,
    onConnect: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.device_settings_not_connected_label),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.device_settings_not_connected_description),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onConnect,
                enabled = !isForceConnecting,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(
                    text = stringResource(
                        if (isNudgeAvailable) R.string.device_settings_not_connected_connect_action
                        else R.string.device_settings_not_connected_open_settings_action
                    ),
                )
            }
        }
    }
}

@Preview2
@Composable
private fun NotConnectedCardNudgeAvailablePreview() = PreviewWrapper {
    NotConnectedCard(
        isNudgeAvailable = true,
        isForceConnecting = false,
        onConnect = {},
    )
}

@Preview2
@Composable
private fun NotConnectedCardNudgeUnavailablePreview() = PreviewWrapper {
    NotConnectedCard(
        isNudgeAvailable = false,
        isForceConnecting = false,
        onConnect = {},
    )
}

@Preview2
@Composable
private fun NotConnectedCardForceConnectingPreview() = PreviewWrapper {
    NotConnectedCard(
        isNudgeAvailable = true,
        isForceConnecting = true,
        onConnect = {},
    )
}
