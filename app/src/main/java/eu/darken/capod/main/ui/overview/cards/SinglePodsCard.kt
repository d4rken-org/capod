package eu.darken.capod.main.ui.overview.cards

import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.twotone.BatteryChargingFull
import androidx.compose.material.icons.twotone.Hearing
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.monitor.core.cachedBatteryFormatted
import eu.darken.capod.monitor.core.firstSeenFormatted
import eu.darken.capod.monitor.core.getSignalQuality
import eu.darken.capod.monitor.core.lastSeenFormatted
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import eu.darken.capod.pods.core.apple.ble.formatBatteryPercent
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SinglePodsCard(
    device: PodDevice,
    showDebug: Boolean,
    now: Instant,
    onAncModeChange: ((AapSetting.AncMode.Value) -> Unit)? = null,
) {
    val context = LocalContext.current

    val clamped = device.batteryHeadset?.coerceIn(0f, 1f)
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

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .then(if (!device.isLive) Modifier.alpha(0.7f) else Modifier),
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
                    Text(
                        text = device.label ?: "?",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = device.getLabel(context),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                SignalBadge(
                    signalText = device.getSignalQuality(context),
                    bleKeyState = device.bleKeyState,
                    isAapConnected = device.isAapConnected,
                    isLive = device.isLive,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Timestamps
            Text(
                text = stringResource(R.string.last_seen_x, device.lastSeenFormatted(now)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val seenFirst = device.seenFirstAt
            val seenLast = device.seenLastAt
            if (seenFirst != null && seenLast != null && Duration.between(seenFirst, seenLast).toMinutes() >= 1) {
                Text(
                    text = stringResource(R.string.first_seen_x, device.firstSeenFormatted(now)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Central gauge
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 1.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(100.dp),
                    ) {
                        // Track ring
                        CircularProgressIndicator(
                            progress = { 1f },
                            modifier = Modifier.size(100.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            strokeWidth = 8.dp,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            strokeCap = StrokeCap.Round,
                        )

                        // Progress ring
                        if (clamped != null) {
                            CircularProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier.size(100.dp),
                                color = ringColor,
                                strokeWidth = 8.dp,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                strokeCap = StrokeCap.Round,
                            )
                        }

                        // Battery text inside ring
                        Text(
                            text = formatBatteryPercent(context, device.batteryHeadset),
                            style = MaterialTheme.typography.headlineMedium,
                            color = if (device.batteryHeadset != null) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Status chips
                    FlowRow(
                        modifier = Modifier.animateContentSize(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (device.isHeadsetBeingCharged == true) {
                            StatusChip(
                                icon = Icons.TwoTone.BatteryChargingFull,
                                label = stringResource(R.string.pods_charging_label),
                            )
                        }
                        if (device.isBeingWorn == true) {
                            StatusChip(
                                icon = Icons.TwoTone.Hearing,
                                label = stringResource(R.string.pods_inear_label),
                            )
                        }
                    }
                }
            }

            // Cached battery indicator
            if (device.isBatteryCached) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.battery_cached_label, device.cachedBatteryFormatted(now)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ANC mode selector
            val ancMode = device.ancMode
            if (device.isAapConnected && device.hasAncControl && ancMode != null) {
                Spacer(modifier = Modifier.height(12.dp))
                AncModeSelector(
                    currentMode = ancMode.current,
                    supportedModes = ancMode.supported,
                    onModeSelected = { onAncModeChange?.invoke(it) },
                    pendingMode = device.pendingAncMode,
                )
            }

            // Debug info
            if (showDebug) {
                DebugSection(rawDataHex = device.rawDataHex)
            }
        }
    }
}

@Preview2
@Composable
private fun SinglePodsCardPreview() = PreviewWrapper {
    SinglePodsCard(device = MockPodDataProvider.singlePodMonitored(), showDebug = false, now = Instant.now())
}

@Preview2
@Composable
private fun SinglePodsCardDebugPreview() = PreviewWrapper {
    SinglePodsCard(device = MockPodDataProvider.singlePodMonitored(), showDebug = true, now = Instant.now())
}

@Preview2
@Composable
private fun SinglePodsCardCachedOnlyPreview() = PreviewWrapper {
    SinglePodsCard(device = MockPodDataProvider.singlePodCachedOnly(), showDebug = false, now = Instant.now())
}
