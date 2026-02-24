package eu.darken.capod.main.ui.overview.cards

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.BatteryChargingFull
import androidx.compose.material.icons.twotone.Hearing
import androidx.compose.material.icons.twotone.SettingsInputAntenna
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.capod.R
import eu.darken.capod.pods.core.HasChargeDetection
import eu.darken.capod.pods.core.HasEarDetection
import eu.darken.capod.pods.core.SinglePodDevice
import eu.darken.capod.pods.core.firstSeenFormatted
import eu.darken.capod.pods.core.formatBatteryPercent
import eu.darken.capod.pods.core.getSignalQuality
import eu.darken.capod.pods.core.lastSeenFormatted
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.common.compose.preview.MockPodDataProvider
import java.time.Duration
import java.time.Instant

@Composable
fun SinglePodsCard(
    device: SinglePodDevice,
    showDebug: Boolean,
    now: Instant,
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
            // Header: name + device icon on left, signal on right
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Image(
                    painter = painterResource(device.iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.meta.profile?.label ?: "?",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = device.getLabel(context),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Signal quality + antenna icon
                Text(
                    text = device.getSignalQuality(context),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.TwoTone.SettingsInputAntenna,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Last seen
            Text(
                text = stringResource(R.string.last_seen_x, device.lastSeenFormatted(now)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // First seen (only show if > 1 minute has passed)
            if (Duration.between(device.seenFirstAt, device.seenLastAt).toMinutes() >= 1) {
                Text(
                    text = stringResource(R.string.first_seen_x, device.firstSeenFormatted(now)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Battery level
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = formatBatteryPercent(context, device.batteryHeadsetPercent),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(48.dp),
                )

                LinearProgressIndicator(
                    progress = { device.batteryHeadsetPercent ?: 0f },
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp),
                )

                Row(
                    modifier = Modifier.width(60.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (device is HasChargeDetection && device.isHeadsetBeingCharged) {
                        Icon(
                            imageVector = Icons.TwoTone.BatteryChargingFull,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }

                    if (device is HasEarDetection && device.isBeingWorn) {
                        Icon(
                            imageVector = Icons.TwoTone.Hearing,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            // Debug info
            if (showDebug) {
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "--- Debug ---",
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
private fun SinglePodsCardWearingPreview() = PreviewWrapper {
    SinglePodsCard(device = MockPodDataProvider.airPodsMax(), showDebug = false, now = Instant.now())
}

@Preview2
@Composable
private fun SinglePodsCardChargingPreview() = PreviewWrapper {
    SinglePodsCard(device = MockPodDataProvider.airPodsMaxCharging(), showDebug = false, now = Instant.now())
}

@Preview2
@Composable
private fun SinglePodsCardDebugPreview() = PreviewWrapper {
    SinglePodsCard(device = MockPodDataProvider.beatsSolo3(), showDebug = true, now = Instant.now())
}
