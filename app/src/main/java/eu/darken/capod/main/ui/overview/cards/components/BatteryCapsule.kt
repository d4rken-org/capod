package eu.darken.capod.main.ui.overview.cards.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.common.theming.fillColor
import eu.darken.capod.monitor.core.battery.BatteryTier
import eu.darken.capod.monitor.core.battery.batteryTier
import eu.darken.capod.pods.core.apple.ble.batteryProgress

private val CapsuleShape = RoundedCornerShape(6.dp)

@Composable
fun BatteryCapsule(
    percent: Float,
    modifier: Modifier = Modifier,
) {
    val tier = batteryTier(percent)
    val isKnown = tier != BatteryTier.UNKNOWN
    val animatedFraction by animateFloatAsState(
        targetValue = batteryProgress(percent),
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "batteryFill",
    )

    val barColor = tier.fillColor()

    Box(
        modifier = modifier
            .clip(CapsuleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (isKnown) {
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

@Preview2
@Composable
private fun BatteryCapsuleFullPreview() = PreviewWrapper {
    BatteryCapsule(percent = 1.0f, modifier = Modifier.width(120.dp).height(8.dp))
}

@Preview2
@Composable
private fun BatteryCapsuleLowPreview() = PreviewWrapper {
    BatteryCapsule(percent = 0.10f, modifier = Modifier.width(120.dp).height(8.dp))
}

@Preview2
@Composable
private fun BatteryCapsuleUnknownPreview() = PreviewWrapper {
    BatteryCapsule(percent = -1f, modifier = Modifier.width(120.dp).height(8.dp))
}
