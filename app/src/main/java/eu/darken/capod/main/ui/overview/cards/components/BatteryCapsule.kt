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
