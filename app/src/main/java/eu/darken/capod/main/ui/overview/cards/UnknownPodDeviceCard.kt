package eu.darken.capod.main.ui.overview.cards

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.darken.capod.R
import eu.darken.capod.main.ui.overview.cards.components.SignalIndicator
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.common.compose.preview.MockPodDataProvider
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.ble.devices.ApplePods

@Composable
fun UnknownPodDeviceCard(
    device: PodDevice,
    showDebug: Boolean,
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            // Header: label + signal indicator (sits right after the text)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = device.getLabel(context),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                SignalIndicator(
                    signalQuality = device.rssiQuality,
                    isLive = device.isLive,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (device.ble is ApplePods) {
                    stringResource(R.string.pods_unknown_contact_dev)
                } else {
                    stringResource(R.string.pods_unknown_label)
                },
                style = MaterialTheme.typography.bodyMedium,
            )

            if (showDebug) {
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.pods_unknown_raw_data_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = device.rawDataHex.joinToString("\n"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview2
@Composable
private fun UnknownPodDeviceCardPreview() = PreviewWrapper {
    UnknownPodDeviceCard(device = MockPodDataProvider.unknownMonitored(), showDebug = false)
}

@Preview2
@Composable
private fun UnknownPodDeviceCardDebugPreview() = PreviewWrapper {
    UnknownPodDeviceCard(device = MockPodDataProvider.unknownMonitored(), showDebug = true)
}
