package eu.darken.capod.main.ui.devicesettings.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper

/**
 * Three-band glyph for a settings row's trailing slot. Bands are `0..100` with 50 neutral; a
 * boosted band draws taller than the neutral reference line, a cut one shorter.
 */
@Composable
fun EqMiniBars(
    low: Int,
    mid: Int,
    high: Int,
    isUnconfigured: Boolean,
    modifier: Modifier = Modifier,
) {
    val barColor = when {
        isUnconfigured -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        else -> MaterialTheme.colorScheme.primary
    }
    val referenceColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isUnconfigured) 0.4f else 1f)
    val bands = when {
        isUnconfigured -> listOf(NEUTRAL, NEUTRAL, NEUTRAL)
        else -> listOf(low, mid, high)
    }

    Canvas(modifier = modifier.size(30.dp)) {
        val spacing = 3.dp.toPx()
        val cornerRadius = CornerRadius(1.5.dp.toPx())
        val barWidth = (size.width - spacing * (bands.size - 1)) / bands.size
        val minHeight = size.height * MIN_BAR_FRACTION
        val heightSpan = size.height - minHeight

        val neutralHeight = minHeight + heightSpan * (NEUTRAL / 100f)
        drawRect(
            color = referenceColor,
            topLeft = Offset(0f, size.height - neutralHeight),
            size = Size(size.width, 1.dp.toPx()),
        )

        bands.forEachIndexed { index, band ->
            val barHeight = minHeight + heightSpan * (band.coerceIn(0, 100) / 100f)
            drawRoundRect(
                color = barColor,
                topLeft = Offset(index * (barWidth + spacing), size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = cornerRadius,
            )
        }
    }
}

private const val NEUTRAL = 50
private const val MIN_BAR_FRACTION = 0.15f

@Preview2
@Composable
private fun EqMiniBarsPreview() = PreviewWrapper {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EqMiniBars(low = 85, mid = 50, high = 20, isUnconfigured = false)
        EqMiniBars(low = 15, mid = 40, high = 90, isUnconfigured = false)
        EqMiniBars(low = 50, mid = 50, high = 50, isUnconfigured = false)
        EqMiniBars(low = 50, mid = 50, high = 50, isUnconfigured = true)
    }
}
