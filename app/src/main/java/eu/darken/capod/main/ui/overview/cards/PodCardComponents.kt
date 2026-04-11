package eu.darken.capod.main.ui.overview.cards

import androidx.annotation.VisibleForTesting
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.twotone.BatteryChargingFull
import androidx.compose.material.icons.twotone.Bluetooth
import androidx.compose.material.icons.twotone.Hearing
import androidx.compose.material.icons.twotone.KeyboardVoice
import androidx.compose.material.icons.twotone.LinkOff
import androidx.compose.material.icons.twotone.Lock
import androidx.compose.material.icons.twotone.SettingsInputAntenna
import androidx.compose.material.icons.twotone.Stars
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.darken.capod.R
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.monitor.core.BleKeyState
import eu.darken.capod.monitor.core.ConnectionState
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting

private val CapsuleShape = RoundedCornerShape(6.dp)

@Composable
fun BatteryCapsule(
    percent: Float,
    modifier: Modifier = Modifier,
) {
    val clamped = if (percent >= 0f) percent.coerceIn(0f, 1f) else -1f
    val animatedFraction by animateFloatAsState(
        targetValue = if (clamped >= 0f) clamped else 0f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "batteryFill",
    )

    val barColor = when {
        clamped < 0f -> MaterialTheme.colorScheme.surfaceVariant
        clamped > 0.30f -> MaterialTheme.colorScheme.primary
        clamped >= 0.15f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }

    Box(
        modifier = modifier
            .clip(CapsuleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (clamped >= 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = animatedFraction)
                    .clip(CapsuleShape)
                    .background(barColor),
            )
        }
    }
}

@Composable
fun StatusChip(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatusChipRow(
    isCharging: Boolean,
    isInEar: Boolean,
    showEarDetection: Boolean,
    isMicrophone: Boolean,
    showMicrophone: Boolean,
    chargingLabel: String,
    inEarLabel: String,
    microphoneLabel: String,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.animateContentSize(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (isCharging) {
            StatusChip(
                icon = Icons.TwoTone.BatteryChargingFull,
                label = chargingLabel,
            )
        }
        if (showMicrophone && isMicrophone) {
            StatusChip(
                icon = Icons.TwoTone.KeyboardVoice,
                label = microphoneLabel,
            )
        }
        if (showEarDetection && isInEar) {
            StatusChip(
                icon = Icons.TwoTone.Hearing,
                label = inEarLabel,
            )
        }
    }
}

@VisibleForTesting
internal enum class SignalLevel { DISCONNECTED, BARS_0, BARS_1, BARS_2, BARS_3, BARS_4 }

@VisibleForTesting
internal fun signalLevelOf(signalQuality: Float, isLive: Boolean): SignalLevel {
    if (!isLive) return SignalLevel.DISCONNECTED
    if (!signalQuality.isFinite()) return SignalLevel.BARS_0
    val q = signalQuality.coerceIn(0f, 1f)
    return when {
        q >= 0.80f -> SignalLevel.BARS_4
        q >= 0.60f -> SignalLevel.BARS_3
        q >= 0.40f -> SignalLevel.BARS_2
        q >= 0.20f -> SignalLevel.BARS_1
        else -> SignalLevel.BARS_0
    }
}

@Composable
fun SignalIndicator(
    signalQuality: Float,
    isLive: Boolean,
    modifier: Modifier = Modifier,
) {
    val level = signalLevelOf(signalQuality, isLive)
    val description = when (level) {
        SignalLevel.DISCONNECTED -> stringResource(R.string.signal_indicator_disconnected_cd)
        SignalLevel.BARS_0 -> stringResource(R.string.signal_indicator_bars_cd, 0)
        SignalLevel.BARS_1 -> stringResource(R.string.signal_indicator_bars_cd, 1)
        SignalLevel.BARS_2 -> stringResource(R.string.signal_indicator_bars_cd, 2)
        SignalLevel.BARS_3 -> stringResource(R.string.signal_indicator_bars_cd, 3)
        SignalLevel.BARS_4 -> stringResource(R.string.signal_indicator_bars_cd, 4)
    }
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    val rootModifier = modifier
        .size(12.dp)
        .semantics(mergeDescendants = true) { contentDescription = description }

    if (level == SignalLevel.DISCONNECTED) {
        Icon(
            imageVector = Icons.TwoTone.LinkOff,
            contentDescription = null,
            modifier = rootModifier,
            tint = tint,
        )
        return
    }

    val activeBars = when (level) {
        SignalLevel.BARS_0 -> 0
        SignalLevel.BARS_1 -> 1
        SignalLevel.BARS_2 -> 2
        SignalLevel.BARS_3 -> 3
        SignalLevel.BARS_4 -> 4
        SignalLevel.DISCONNECTED -> 0 // unreachable; handled above
    }
    val inactiveColor = tint.copy(alpha = 0.3f)
    Canvas(modifier = rootModifier) {
        val barCount = 4
        val gapWidth = size.width * 0.12f
        val totalGap = gapWidth * (barCount - 1)
        val barWidth = (size.width - totalGap) / barCount
        val corner = CornerRadius(barWidth * 0.4f, barWidth * 0.4f)
        for (i in 0 until barCount) {
            val heightFraction = (i + 1).toFloat() / barCount
            val barHeight = size.height * heightFraction
            val x = i * (barWidth + gapWidth)
            val y = size.height - barHeight
            drawRoundRect(
                color = if (i < activeBars) tint else inactiveColor,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = corner,
            )
        }
    }
}

@Composable
fun DeviceConnectionBadge(
    state: ConnectionState,
    modifier: Modifier = Modifier,
) {
    if (!state.hasBleData && state.bleKeyState == BleKeyState.NONE && !state.isAapConnected) return
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (state.hasBleData) {
                Icon(
                    imageVector = Icons.TwoTone.SettingsInputAntenna,
                    contentDescription = stringResource(R.string.signal_badge_ble_cd),
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.bleKeyState != BleKeyState.NONE) {
                Icon(
                    imageVector = Icons.Outlined.Key,
                    contentDescription = stringResource(R.string.signal_badge_key_irk_cd),
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.bleKeyState == BleKeyState.IRK_AND_ENCRYPTED) {
                Icon(
                    imageVector = Icons.TwoTone.Lock,
                    contentDescription = stringResource(R.string.signal_badge_key_encrypted_cd),
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.isAapConnected) {
                Icon(
                    imageVector = Icons.TwoTone.Bluetooth,
                    contentDescription = stringResource(R.string.signal_badge_aap_cd),
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun DebugSection(
    rawDataHex: List<String>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "--- Debug ---",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = rawDataHex.joinToString("\n"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun AncModeSelector(
    modifier: Modifier = Modifier,
    currentMode: AapSetting.AncMode.Value,
    supportedModes: List<AapSetting.AncMode.Value>,
    onModeSelected: (AapSetting.AncMode.Value) -> Unit,
    pendingMode: AapSetting.AncMode.Value? = null,
    enabled: Boolean = true,
) {
    val displayMode = pendingMode ?: currentMode
    Box(modifier = modifier.fillMaxWidth()) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            supportedModes.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = mode == displayMode,
                    onClick = { onModeSelected(mode) },
                    enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(index, supportedModes.size),
                    colors = if (pendingMode != null && mode == displayMode) {
                        SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            activeContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    } else {
                        SegmentedButtonDefaults.colors()
                    },
                    label = {
                        Text(
                            text = when (mode) {
                                AapSetting.AncMode.Value.OFF -> stringResource(R.string.anc_mode_off)
                                AapSetting.AncMode.Value.ON -> stringResource(R.string.anc_mode_on)
                                AapSetting.AncMode.Value.TRANSPARENCY -> stringResource(R.string.anc_mode_transparency)
                                AapSetting.AncMode.Value.ADAPTIVE -> stringResource(R.string.anc_mode_adaptive)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }
    }
}

@Composable
fun ConversationAwarenessToggle(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.conversation_awareness_label),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
        )
    }
}

@Preview2
@Composable
private fun DeviceConnectionBadgeAllIconsPreview() = PreviewWrapper {
    DeviceConnectionBadge(
        state = ConnectionState(
            hasBleData = true,
            bleKeyState = BleKeyState.IRK_AND_ENCRYPTED,
            isAapConnected = true,
            rssiQuality = 1f,
        ),
    )
}

@Preview2
@Composable
private fun DeviceConnectionBadgeBleOnlyPreview() = PreviewWrapper {
    DeviceConnectionBadge(
        state = ConnectionState(
            hasBleData = true,
            bleKeyState = BleKeyState.NONE,
            isAapConnected = false,
            rssiQuality = 0.7f,
        ),
    )
}

@Preview2
@Composable
private fun DeviceConnectionBadgeBleIrkPreview() = PreviewWrapper {
    DeviceConnectionBadge(
        state = ConnectionState(
            hasBleData = true,
            bleKeyState = BleKeyState.IRK_ONLY,
            isAapConnected = false,
            rssiQuality = 0.5f,
        ),
    )
}

@Preview2
@Composable
private fun DeviceConnectionBadgeAapOnlyPreview() = PreviewWrapper {
    DeviceConnectionBadge(
        state = ConnectionState(
            hasBleData = false,
            bleKeyState = BleKeyState.NONE,
            isAapConnected = true,
            rssiQuality = 0f,
        ),
    )
}

@Preview2
@Composable
private fun DeviceConnectionBadgeEmptyPreview() = PreviewWrapper {
    DeviceConnectionBadge(
        state = ConnectionState(
            hasBleData = false,
            bleKeyState = BleKeyState.NONE,
            isAapConnected = false,
            rssiQuality = 0f,
        ),
    )
}

@Preview2
@Composable
private fun SignalIndicatorPreviewsGroup() = PreviewWrapper {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SignalIndicator(signalQuality = 0.10f, isLive = true)
        SignalIndicator(signalQuality = 0.35f, isLive = true)
        SignalIndicator(signalQuality = 0.55f, isLive = true)
        SignalIndicator(signalQuality = 0.75f, isLive = true)
        SignalIndicator(signalQuality = 0.95f, isLive = true)
        SignalIndicator(signalQuality = Float.NaN, isLive = true)
        SignalIndicator(signalQuality = -0.3f, isLive = true)
        SignalIndicator(signalQuality = 0.85f, isLive = false)
    }
}
