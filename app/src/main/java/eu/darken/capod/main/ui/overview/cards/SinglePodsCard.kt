package eu.darken.capod.main.ui.overview.cards

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.twotone.Tune
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
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
import eu.darken.capod.monitor.core.visibleAncModes
import eu.darken.capod.main.ui.overview.cards.components.AncModeSelector
import eu.darken.capod.main.ui.overview.cards.components.CompactBatterySummary
import eu.darken.capod.main.ui.overview.cards.components.DebugSection
import eu.darken.capod.main.ui.overview.cards.components.DeviceConnectionBadge
import eu.darken.capod.main.ui.overview.cards.components.MissingPairedDeviceBanner
import eu.darken.capod.main.ui.overview.cards.components.SignalIndicator
import eu.darken.capod.main.ui.overview.cards.components.StatusChip
import eu.darken.capod.common.SystemTimeSource
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.common.compose.preview.MockPodDataProvider
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.monitor.core.cachedBatteryFormatted
import eu.darken.capod.pods.core.apple.aap.AapPodState
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import eu.darken.capod.pods.core.apple.ble.batteryProgress
import eu.darken.capod.pods.core.apple.ble.formatBatteryPercent
import eu.darken.capod.pods.core.apple.ble.isKnownBattery
import java.time.Instant

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SinglePodsCard(
    device: PodDevice,
    isPro: Boolean = true,
    showDebug: Boolean,
    now: Instant,
    isCollapsed: Boolean = false,
    onToggleCollapse: (() -> Unit)? = null,
    onAncModeChange: ((AapSetting.AncMode.Value) -> Unit)? = null,
    onUpgrade: (() -> Unit)? = null,
    onDeviceSettings: (() -> Unit)? = null,
    onEditProfile: (() -> Unit)? = null,
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
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize(),
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (onToggleCollapse != null) {
                            Modifier.clickable(onClick = onToggleCollapse)
                        } else {
                            Modifier
                        }
                    ),
            ) {
                Image(
                    painter = painterResource(device.iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                )

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = device.label ?: "?",
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = device.getLabel(context),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        DeviceConnectionBadge(
                            state = device.connectionState,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }

                if (device.profileId != null && onDeviceSettings != null) {
                    OutlinedIconButton(onClick = onDeviceSettings) {
                        Icon(
                            imageVector = Icons.TwoTone.Tune,
                            contentDescription = stringResource(R.string.device_settings_open_cd),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (!isCollapsed
                && device.profileId != null
                && !device.hasSelectedPairedDevice
                && onEditProfile != null
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                MissingPairedDeviceBanner(onClick = onEditProfile)
            }

            if (isCollapsed) {
                CompactBatterySummary(device = device)
            } else {
                SinglePodsCardExpanded(
                    device = device,
                    showDebug = showDebug,
                    now = now,
                    onAncModeChange = onAncModeChange,
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.SinglePodsCardExpanded(
    device: PodDevice,
    showDebug: Boolean,
    now: Instant,
    onAncModeChange: ((AapSetting.AncMode.Value) -> Unit)?,
) {
    val context = LocalContext.current

    val percent = device.batteryHeadset
    val isKnown = isKnownBattery(percent)
    val animatedProgress by animateFloatAsState(
        targetValue = batteryProgress(percent),
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "gaugeProgress",
    )

    val ringColor = when {
        !isKnown -> MaterialTheme.colorScheme.surfaceVariant
        percent > 0.30f -> MaterialTheme.colorScheme.primary
        percent >= 0.15f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Central gauge
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (!device.isLive) Modifier.alpha(0.7f) else Modifier),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(88.dp),
            ) {
                // Track ring
                CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.size(88.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    strokeWidth = 8.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round,
                )

                // Progress ring
                if (isKnown) {
                    CircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.size(88.dp),
                        color = ringColor,
                        strokeWidth = 8.dp,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round,
                    )
                }

                // Battery text inside ring
                Text(
                    text = formatBatteryPercent(context, percent),
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (isKnown) {
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
                when (val headsetState = device.headsetChargingState
                    ?: device.isHeadsetBeingCharged?.let { if (it) AapPodState.ChargingState.CHARGING else null }) {
                    AapPodState.ChargingState.CHARGING_OPTIMIZED -> StatusChip(
                        icon = Icons.TwoTone.BatteryChargingFull,
                        label = stringResource(R.string.pods_charging_optimized_label),
                    )
                    AapPodState.ChargingState.CHARGING -> StatusChip(
                        icon = Icons.TwoTone.BatteryChargingFull,
                        label = stringResource(R.string.pods_charging_label),
                    )
                    else -> Unit
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
    if (device.isBatteryCached && !device.isLive) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.battery_cached_label, device.cachedBatteryFormatted(now)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.End),
        )
    }

    // ANC mode selector
    val ancMode = device.ancMode
    if (device.isAapConnected && device.hasAncControl && ancMode != null) {
        Spacer(modifier = Modifier.height(12.dp))
        AncModeSelector(
            currentMode = ancMode.current,
                    supportedModes = device.visibleAncModes,
            onModeSelected = { onAncModeChange?.invoke(it) },
            pendingMode = device.pendingAncMode,
        )
    }

    // Debug info
    if (showDebug) {
        DebugSection(rawDataHex = device.rawDataHex)
    }
}

@Preview2
@Composable
private fun SinglePodsCardFullPreview() = PreviewWrapper {
    SinglePodsCard(
        device = MockPodDataProvider.singlePodMonitoredWithAap(),
        showDebug = false,
        now = SystemTimeSource.now(),
        onDeviceSettings = {},
    )
}

@Preview2
@Composable
private fun SinglePodsCardMinimalPreview() = PreviewWrapper {
    SinglePodsCard(
        device = MockPodDataProvider.singlePodMonitored(),
        showDebug = false,
        now = SystemTimeSource.now(),
    )
}

@Preview2
@Composable
private fun SinglePodsCardCachedPreview() = PreviewWrapper {
    SinglePodsCard(
        device = MockPodDataProvider.singlePodCachedOnly(),
        showDebug = false,
        now = SystemTimeSource.now(),
    )
}

@Preview2
@Composable
private fun SinglePodsCardCollapsedPreview() = PreviewWrapper {
    SinglePodsCard(
        device = MockPodDataProvider.singlePodMonitored(),
        showDebug = false,
        now = SystemTimeSource.now(),
        isCollapsed = true,
        onToggleCollapse = {},
    )
}

@Preview2
@Composable
private fun SinglePodsCardMissingAddressPreview() = PreviewWrapper {
    SinglePodsCard(
        device = MockPodDataProvider.singlePodMissingPairedDevice(),
        showDebug = false,
        now = SystemTimeSource.now(),
        onEditProfile = {},
    )
}
