package eu.darken.capod.main.ui.overview.cards.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.BatteryUnknown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import eu.darken.capod.R
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.ble.batteryProgress
import eu.darken.capod.pods.core.apple.ble.formatBatteryPercent
import eu.darken.capod.pods.core.apple.ble.isKnownBattery

@Composable
fun CompactBatterySummary(
    device: PodDevice,
    modifier: Modifier = Modifier,
) {
    val hasAnyBattery = isKnownBattery(device.batteryLeft)
            || isKnownBattery(device.batteryRight)
            || isKnownBattery(device.batteryHeadset)
            || isKnownBattery(device.batteryCase)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .then(if (!device.isLive) Modifier.alpha(0.7f) else Modifier),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                !hasAnyBattery -> EmptyBatteryRow()
                device.hasDualPods -> DualPodsRow(device)
                else -> SinglePodRow(device)
            }
        }
    }
}

@Composable
private fun RowScope.DualPodsRow(device: PodDevice) {
    MiniPodRing(
        iconRes = device.leftPodIcon,
        percent = device.batteryLeft,
    )
    Spacer(modifier = Modifier.width(16.dp))
    MiniPodRing(
        iconRes = device.rightPodIcon,
        percent = device.batteryRight,
    )

    if (device.hasCase && isKnownBattery(device.batteryCase)) {
        Spacer(modifier = Modifier.weight(1f))
        MiniCaseCluster(device = device)
    }
}

@Composable
private fun RowScope.SinglePodRow(device: PodDevice) {
    Spacer(modifier = Modifier.weight(1f))
    MiniPodRing(
        iconRes = null,
        percent = device.batteryHeadset,
    )
    if (device.hasCase && isKnownBattery(device.batteryCase)) {
        Spacer(modifier = Modifier.weight(1f))
        MiniCaseCluster(device = device)
    }
    Spacer(modifier = Modifier.weight(1f))
}

@Composable
private fun RowScope.EmptyBatteryRow() {
    Spacer(modifier = Modifier.weight(1f))
    Icon(
        imageVector = Icons.AutoMirrored.TwoTone.BatteryUnknown,
        contentDescription = null,
        modifier = Modifier.size(20.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.width(8.dp))
    Text(
        text = stringResource(R.string.battery_unavailable_label),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.weight(1f))
}

@Composable
private fun MiniPodRing(
    iconRes: Int?,
    percent: Float,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isKnown = isKnownBattery(percent)
    val animatedProgress by animateFloatAsState(
        targetValue = batteryProgress(percent),
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "miniGaugeProgress",
    )

    val ringColor = when {
        !isKnown -> MaterialTheme.colorScheme.surfaceVariant
        percent > 0.30f -> MaterialTheme.colorScheme.primary
        percent >= 0.15f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(28.dp),
        ) {
            CircularProgressIndicator(
                progress = { 1f },
                modifier = Modifier.size(28.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                strokeWidth = 3.dp,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round,
            )
            if (isKnown) {
                CircularProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.size(28.dp),
                    color = ringColor,
                    strokeWidth = 3.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round,
                )
            }
            if (iconRes != null) {
                Image(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = formatBatteryPercent(context, percent),
            style = MaterialTheme.typography.titleSmall,
            color = if (isKnown) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun MiniCaseCluster(
    device: PodDevice,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(device.caseIcon),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        BatteryCapsule(
            percent = device.batteryCase,
            modifier = Modifier
                .width(36.dp)
                .height(6.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = formatBatteryPercent(context, device.batteryCase),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
