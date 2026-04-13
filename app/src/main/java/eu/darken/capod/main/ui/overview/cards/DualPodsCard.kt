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
import androidx.compose.material.icons.twotone.BatteryChargingFull
import androidx.compose.material.icons.twotone.GridView
import androidx.compose.material.icons.twotone.Tune
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import eu.darken.capod.common.SystemTimeSource
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.common.compose.preview.MockPodDataProvider
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.monitor.core.cachedBatteryFormatted
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import eu.darken.capod.pods.core.apple.ble.devices.DualApplePods
import eu.darken.capod.pods.core.apple.ble.devices.DualApplePods.LidState
import eu.darken.capod.pods.core.apple.ble.devices.HasPodStyle
import eu.darken.capod.pods.core.apple.ble.formatBatteryPercent
import eu.darken.capod.pods.core.apple.ble.toBatteryFloat
import eu.darken.capod.pods.core.apple.ble.toBatteryOrNull
import java.time.Instant

@Composable
fun DualPodsCard(
    device: PodDevice,
    isPro: Boolean = true,
    showDebug: Boolean,
    now: Instant,
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
                    val deviceLabel = buildString {
                        append(device.getLabel(context))
                        val podStyle = device.ble as? HasPodStyle
                        if (podStyle != null && showDebug) {
                            append(" (${podStyle.podStyle.getColor(context)})")
                        }
                        val dualApple = device.ble as? DualApplePods
                        if (dualApple != null && showDebug) {
                            append(" [${dualApple.primaryPod.name}]")
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = deviceLabel,
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
                    IconButton(onClick = onDeviceSettings) {
                        Icon(
                            imageVector = Icons.TwoTone.Tune,
                            contentDescription = stringResource(R.string.device_settings_open_cd),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (device.profileId != null && device.address == null && onEditProfile != null) {
                    IconButton(onClick = onEditProfile) {
                        Icon(
                            imageVector = Icons.TwoTone.Warning,
                            contentDescription = stringResource(R.string.overview_card_missing_paired_device_cd),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Circular battery gauges side by side
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (!device.isLive) Modifier.alpha(0.7f) else Modifier),
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 4.dp,
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
                            batteryPercent = device.batteryLeft.toBatteryFloat(),
                            isCharging = device.isLeftPodCharging ?: false,
                            isInEar = device.isLeftInEar ?: false,
                            showEarDetection = device.hasEarDetection && device.hasDualPods,
                            isMicrophone = device.isLeftPodMicrophone ?: false,
                            showMicrophone = device.hasDualMicrophone,
                            modifier = Modifier.weight(1f),
                        )

                        PodGauge(
                            iconRes = device.rightPodIcon,
                            batteryPercent = device.batteryRight.toBatteryFloat(),
                            isCharging = device.isRightPodCharging ?: false,
                            isInEar = device.isRightInEar ?: false,
                            showEarDetection = device.hasEarDetection && device.hasDualPods,
                            isMicrophone = device.isRightPodMicrophone ?: false,
                            showMicrophone = device.hasDualMicrophone,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    // Case row
                    if (device.hasCase) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        )

                        CaseRow(device = device)
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
                Spacer(modifier = Modifier.height(8.dp))
                val cycleMask = (device.listeningModeCycle?.modeMask ?: 0x0E)
                val cycleBits = mapOf(
                    AapSetting.AncMode.Value.OFF to 0x01,
                    AapSetting.AncMode.Value.ON to 0x02,
                    AapSetting.AncMode.Value.TRANSPARENCY to 0x04,
                    AapSetting.AncMode.Value.ADAPTIVE to 0x08,
                )
                val visibleModes = ancMode.supported.filter { mode ->
                    val bit = cycleBits[mode] ?: return@filter true
                    (cycleMask and bit) != 0
                }
                AncModeSelector(
                    currentMode = ancMode.current,
                    supportedModes = visibleModes,
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

@Composable
private fun PodGauge(
    iconRes: Int,
    batteryPercent: Float,
    isCharging: Boolean,
    isInEar: Boolean,
    showEarDetection: Boolean,
    isMicrophone: Boolean,
    showMicrophone: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clamped = if (batteryPercent >= 0f) batteryPercent.coerceIn(0f, 1f) else -1f
    val animatedProgress by animateFloatAsState(
        targetValue = if (clamped >= 0f) clamped else 0f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "gaugeProgress",
    )

    val ringColor = when {
        clamped < 0f -> MaterialTheme.colorScheme.surfaceVariant
        clamped > 0.30f -> MaterialTheme.colorScheme.primary
        clamped >= 0.15f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Ring with icon inside
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(68.dp),
        ) {
            // Track ring
            CircularProgressIndicator(
                progress = { 1f },
                modifier = Modifier.size(68.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                strokeWidth = 6.dp,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round,
            )

            // Progress ring
            if (clamped >= 0f) {
                CircularProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.size(68.dp),
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
            text = formatBatteryPercent(context, batteryPercent.toBatteryOrNull()),
            style = MaterialTheme.typography.titleMedium,
            color = if (batteryPercent >= 0f) {
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
    device: PodDevice,
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
            text = formatBatteryPercent(context, device.batteryCase),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(end = 8.dp),
        )

        BatteryCapsule(
            percent = device.batteryCase.toBatteryFloat(),
            modifier = Modifier
                .weight(1f)
                .height(8.dp),
        )

        Spacer(modifier = Modifier.width(8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (device.isCaseCharging == true) {
                StatusChip(
                    icon = Icons.TwoTone.BatteryChargingFull,
                    label = stringResource(R.string.pods_charging_label),
                )
            }

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

@Preview2
@Composable
private fun DualPodsCardFullPreview() = PreviewWrapper {
    DualPodsCard(
        device = MockPodDataProvider.dualPodMonitoredWithAap(),
        showDebug = false,
        now = SystemTimeSource.now(),
        isPro = false,
        onDeviceSettings = {},
    )
}

@Preview2
@Composable
private fun DualPodsCardMinimalPreview() = PreviewWrapper {
    DualPodsCard(
        device = MockPodDataProvider.dualPodMonitored(),
        showDebug = false,
        now = SystemTimeSource.now(),
    )
}

@Preview2
@Composable
private fun DualPodsCardCachedPreview() = PreviewWrapper {
    DualPodsCard(
        device = MockPodDataProvider.dualPodCachedOnly(),
        showDebug = false,
        now = SystemTimeSource.now(),
    )
}

@Preview2
@Composable
private fun DualPodsCardMissingAddressPreview() = PreviewWrapper {
    DualPodsCard(
        device = MockPodDataProvider.dualPodMonitoredMixed(),
        showDebug = false,
        now = SystemTimeSource.now(),
        onEditProfile = {},
    )
}
