package eu.darken.capod.main.ui.devicesettings.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper

@Composable
fun EqBarsChart(
    sets: List<List<Float>>,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outlineVariant

    val bands = sets.firstOrNull() ?: return
    if (bands.size != 8) return

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp),
    ) {
        val barCount = bands.size
        val spacing = 4.dp.toPx()
        val cornerRadius = 4.dp.toPx()
        val barWidth = (size.width - spacing * (barCount - 1)) / barCount

        for (i in bands.indices) {
            val x = i * (barWidth + spacing)
            val normalized = (bands[i] / 100f).coerceIn(0f, 1f)
            val barHeight = (normalized * size.height).coerceAtLeast(2.dp.toPx())

            drawRoundRect(
                color = outline,
                topLeft = Offset(x, 0f),
                size = Size(barWidth, size.height),
                cornerRadius = CornerRadius(cornerRadius),
            )

            drawRoundRect(
                color = primary,
                topLeft = Offset(x, size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(cornerRadius),
            )
        }
    }
}

@Preview2
@Composable
private fun EqBarsChartPreview() = PreviewWrapper {
    EqBarsChart(
        sets = listOf(listOf(10f, 25f, 40f, 60f, 80f, 70f, 50f, 30f)),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
