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
import androidx.compose.material.icons.twotone.GridView
import androidx.compose.material.icons.twotone.Hearing
import androidx.compose.material.icons.twotone.Key
import androidx.compose.material.icons.twotone.KeyboardVoice
import androidx.compose.material.icons.twotone.SettingsInputAntenna
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.capod.R
import eu.darken.capod.pods.core.DualPodDevice
import eu.darken.capod.pods.core.HasCase
import eu.darken.capod.pods.core.HasChargeDetectionDual
import eu.darken.capod.pods.core.HasDualMicrophone
import eu.darken.capod.pods.core.HasEarDetectionDual
import eu.darken.capod.pods.core.HasPodStyle
import eu.darken.capod.pods.core.HasStateDetection
import eu.darken.capod.pods.core.apple.ApplePods
import eu.darken.capod.pods.core.apple.DualApplePods
import eu.darken.capod.pods.core.apple.DualApplePods.LidState
import eu.darken.capod.pods.core.firstSeenFormatted
import eu.darken.capod.pods.core.formatBatteryPercent
import eu.darken.capod.pods.core.getSignalQuality
import eu.darken.capod.pods.core.lastSeenFormatted
import java.time.Duration
import java.time.Instant

@Composable
fun DualPodsCard(
    device: DualPodDevice,
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
            // Header: device icon + name + type on left, signal in top-right
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
                    // Device name + key icon
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = device.meta.profile?.label ?: "?",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (device is ApplePods && device.meta.isIRKMatch) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = if (device.payload.private != null) Icons.TwoTone.Key else Icons.Outlined.Key,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    val deviceLabel = buildString {
                        append(device.getLabel(context))
                        if (device is HasPodStyle && showDebug) {
                            append(" (${device.podStyle.getColor(context)})")
                        }
                        if (device is DualApplePods && showDebug) {
                            append(" [${device.primaryPod.name}]")
                        }
                    }
                    Text(
                        text = deviceLabel,
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

            // Left pod battery
            BatteryRow(
                label = "L",
                iconRes = device.leftPodIcon,
                batteryPercent = device.batteryLeftPodPercent,
                isCharging = (device as? HasChargeDetectionDual)?.isLeftPodCharging ?: false,
                isInEar = (device as? HasEarDetectionDual)?.isLeftPodInEar ?: false,
                showEarDetection = device is HasEarDetectionDual,
                isMicrophone = (device as? HasDualMicrophone)?.isLeftPodMicrophone ?: false,
                showMicrophone = device is HasDualMicrophone,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Right pod battery
            BatteryRow(
                label = "R",
                iconRes = device.rightPodIcon,
                batteryPercent = device.batteryRightPodPercent,
                isCharging = (device as? HasChargeDetectionDual)?.isRightPodCharging ?: false,
                isInEar = (device as? HasEarDetectionDual)?.isRightPodInEar ?: false,
                showEarDetection = device is HasEarDetectionDual,
                isMicrophone = (device as? HasDualMicrophone)?.isRightPodMicrophone ?: false,
                showMicrophone = device is HasDualMicrophone,
            )

            // Case battery + lid state
            if (device is HasCase) {
                Spacer(modifier = Modifier.height(8.dp))

                BatteryRow(
                    label = "C",
                    iconRes = device.caseIcon,
                    batteryPercent = device.batteryCasePercent,
                    isCharging = device.isCaseCharging,
                    isInEar = false,
                    showEarDetection = false,
                    isMicrophone = false,
                    showMicrophone = false,
                )

                // Case lid state (below case row, aligned with label text)
                if (device is DualApplePods) {
                    val lidState = device.caseLidState
                    if (lidState == LidState.OPEN || lidState == LidState.CLOSED) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 32.dp, top = 2.dp),
                        ) {
                            Icon(
                                imageVector = Icons.TwoTone.GridView,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when (lidState) {
                                    LidState.OPEN -> stringResource(R.string.pods_case_status_open_label)
                                    LidState.CLOSED -> stringResource(R.string.pods_case_status_closed_label)
                                    else -> ""
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Connection state
            if (device is HasStateDetection) {
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = device.state.getLabel(context),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

@Composable
private fun BatteryRow(
    label: String,
    iconRes: Int,
    batteryPercent: Float?,
    isCharging: Boolean,
    isInEar: Boolean,
    showEarDetection: Boolean,
    isMicrophone: Boolean,
    showMicrophone: Boolean,
) {
    val context = LocalContext.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "$label: ${formatBatteryPercent(context, batteryPercent)}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(80.dp),
        )

        LinearProgressIndicator(
            progress = { batteryPercent ?: 0f },
            modifier = Modifier
                .weight(1f)
                .height(8.dp),
        )

        // Status icons — fixed width, always rendered, alpha toggles visibility so positions stay stable
        Row(
            modifier = Modifier.width(52.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Icon(
                imageVector = Icons.TwoTone.BatteryChargingFull,
                contentDescription = null,
                modifier = Modifier
                    .size(16.dp)
                    .alpha(if (isCharging) 1f else 0f),
                tint = MaterialTheme.colorScheme.primary,
            )

            Icon(
                imageVector = Icons.TwoTone.KeyboardVoice,
                contentDescription = stringResource(R.string.pods_microphone_label),
                modifier = Modifier
                    .size(16.dp)
                    .alpha(if (showMicrophone && isMicrophone) 1f else 0f),
                tint = MaterialTheme.colorScheme.primary,
            )

            Icon(
                imageVector = Icons.TwoTone.Hearing,
                contentDescription = null,
                modifier = Modifier
                    .size(16.dp)
                    .alpha(if (showEarDetection && isInEar) 1f else 0f),
                tint = MaterialTheme.colorScheme.primary,
            )
        }

    }
}
