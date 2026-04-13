package eu.darken.capod.main.ui.overview.cards.components

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.LinkOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import eu.darken.capod.R
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper

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
