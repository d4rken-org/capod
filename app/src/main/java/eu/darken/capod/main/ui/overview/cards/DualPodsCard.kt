package eu.darken.capod.main.ui.overview.cards

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.twotone.BatteryChargingFull
import androidx.compose.material.icons.twotone.GridView
import androidx.compose.material.icons.twotone.Key
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.darken.capod.R
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.common.compose.preview.MockPodDataProvider
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

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Image(
                    painter = painterResource(device.iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = device.meta.profile?.label ?: "?",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                SignalBadge(signalText = device.getSignalQuality(context))
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Timestamps
            Text(
                text = stringResource(R.string.last_seen_x, device.lastSeenFormatted(now)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (Duration.between(device.seenFirstAt, device.seenLastAt).toMinutes() >= 1) {
                Text(
                    text = stringResource(R.string.first_seen_x, device.firstSeenFormatted(now)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Circular battery gauges side by side
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 1.dp,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        PodGauge(
                            iconRes = device.leftPodIcon,
                            batteryPercent = device.batteryLeftPodPercent,
                            isCharging = (device as? HasChargeDetectionDual)?.isLeftPodCharging ?: false,
                            isInEar = (device as? HasEarDetectionDual)?.isLeftPodInEar ?: false,
                            showEarDetection = device is HasEarDetectionDual,
                            isMicrophone = (device as? HasDualMicrophone)?.isLeftPodMicrophone ?: false,
                            showMicrophone = device is HasDualMicrophone,
                        )

                        PodGauge(
                            iconRes = device.rightPodIcon,
                            batteryPercent = device.batteryRightPodPercent,
                            isCharging = (device as? HasChargeDetectionDual)?.isRightPodCharging ?: false,
                            isInEar = (device as? HasEarDetectionDual)?.isRightPodInEar ?: false,
                            showEarDetection = device is HasEarDetectionDual,
                            isMicrophone = (device as? HasDualMicrophone)?.isRightPodMicrophone ?: false,
                            showMicrophone = device is HasDualMicrophone,
                        )
                    }

                    // Case row
                    if (device is HasCase) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        )

                        CaseRow(device = device)
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
                DebugSection(rawDataHex = device.rawDataHex)
            }
        }
    }
}

@Composable
private fun PodGauge(
    iconRes: Int,
    batteryPercent: Float?,
    isCharging: Boolean,
    isInEar: Boolean,
    showEarDetection: Boolean,
    isMicrophone: Boolean,
    showMicrophone: Boolean,
) {
    val context = LocalContext.current
    val clamped = batteryPercent?.coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = clamped ?: 0f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "gaugeProgress",
    )

    val ringColor = when {
        clamped == null -> MaterialTheme.colorScheme.surfaceVariant
        clamped > 0.30f -> MaterialTheme.colorScheme.primary
        clamped >= 0.15f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Ring with icon inside
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(80.dp),
        ) {
            // Track ring
            CircularProgressIndicator(
                progress = { 1f },
                modifier = Modifier.size(80.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                strokeWidth = 6.dp,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round,
            )

            // Progress ring
            if (clamped != null) {
                CircularProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.size(80.dp),
                    color = ringColor,
                    strokeWidth = 6.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round,
                )
            }

            // Pod icon
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(36.dp),
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Battery percentage
        Text(
            text = formatBatteryPercent(context, batteryPercent),
            style = MaterialTheme.typography.titleMedium,
            color = if (batteryPercent != null) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Status chips
        StatusChipRow(
            isCharging = isCharging,
            isInEar = isInEar,
            showEarDetection = showEarDetection,
            isMicrophone = isMicrophone,
            showMicrophone = showMicrophone,
            chargingLabel = stringResource(R.string.pods_charging_label),
            inEarLabel = stringResource(R.string.pods_inear_label),
            microphoneLabel = stringResource(R.string.pods_microphone_label),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CaseRow(
    device: HasCase,
) {
    val context = LocalContext.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Image(
            painter = painterResource(device.caseIcon),
            contentDescription = null,
            modifier = Modifier.size(28.dp),
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = formatBatteryPercent(context, device.batteryCasePercent),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(48.dp),
        )

        BatteryCapsule(
            percent = device.batteryCasePercent,
            modifier = Modifier
                .weight(1f)
                .height(8.dp),
        )

        Spacer(modifier = Modifier.width(8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (device.isCaseCharging) {
                StatusChip(
                    icon = Icons.TwoTone.BatteryChargingFull,
                    label = stringResource(R.string.pods_charging_label),
                )
            }

            if (device is DualApplePods) {
                val lidState = device.caseLidState
                if (lidState == LidState.OPEN || lidState == LidState.CLOSED) {
                    StatusChip(
                        icon = Icons.TwoTone.GridView,
                        label = when (lidState) {
                            LidState.OPEN -> stringResource(R.string.pods_case_status_open_label)
                            LidState.CLOSED -> stringResource(R.string.pods_case_status_closed_label)
                            else -> ""
                        },
                    )
                }
            }
        }
    }
}

@Preview2
@Composable
private fun DualPodsCardFullChargePreview() = PreviewWrapper {
    DualPodsCard(device = MockPodDataProvider.airPodsProFullCharge(), showDebug = false, now = Instant.now())
}

@Preview2
@Composable
private fun DualPodsCardMixedBatteryPreview() = PreviewWrapper {
    DualPodsCard(device = MockPodDataProvider.airPodsProMixed(), showDebug = false, now = Instant.now())
}

@Preview2
@Composable
private fun DualPodsCardLowBatteryPreview() = PreviewWrapper {
    DualPodsCard(device = MockPodDataProvider.airPodsProLowBattery(), showDebug = false, now = Instant.now())
}

@Preview2
@Composable
private fun DualPodsCardInCasePreview() = PreviewWrapper {
    DualPodsCard(device = MockPodDataProvider.airPodsProInCase(), showDebug = false, now = Instant.now())
}

@Preview2
@Composable
private fun DualPodsCardDebugPreview() = PreviewWrapper {
    DualPodsCard(device = MockPodDataProvider.airPodsProMixed(), showDebug = true, now = Instant.now())
}

@Preview2
@Composable
private fun DualPodsCardNoCasePreview() = PreviewWrapper {
    DualPodsCard(device = MockPodDataProvider.powerBeatsPro(), showDebug = false, now = Instant.now())
}

@Preview2
@Composable
private fun DualPodsCardMicrophonePreview() = PreviewWrapper {
    DualPodsCard(device = MockPodDataProvider.airPodsGen1Wearing(), showDebug = false, now = Instant.now())
}
