package eu.darken.capod.main.ui.widget

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ComposeAncWidgetPreview(
    state: AncWidgetRenderState,
    modifier: Modifier = Modifier,
) {
    when (state) {
        is AncWidgetRenderState.Active -> ActiveAncPreview(state, modifier)
        is AncWidgetRenderState.Message -> MessageAncPreview(state, modifier)
    }
}

@Composable
private fun ActiveAncPreview(
    state: AncWidgetRenderState.Active,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(state.resolvedBgColor))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AncGridRow(state.modes.take(2), state.resolvedIconColor, state.resolvedBgColor)
        if (state.modes.size > 2) {
            AncGridRow(state.modes.drop(2), state.resolvedIconColor, state.resolvedBgColor)
        }
        if (state.theme.showDeviceLabel && state.deviceLabel != null) {
            Text(
                text = state.deviceLabel,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(state.resolvedTextColor),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun AncGridRow(
    items: List<ModeItem>,
    resolvedIconColor: Int,
    resolvedBgColor: Int,
) {
    Row(
        horizontalArrangement = Arrangement.Center,
    ) {
        items.forEach { item ->
            AncPreviewButton(item, resolvedIconColor, resolvedBgColor)
        }
    }
}

@Composable
private fun AncPreviewButton(
    item: ModeItem,
    resolvedIconColor: Int,
    resolvedBgColor: Int,
) {
    val bgColor = when (item.state) {
        ButtonState.ACTIVE -> Color(resolvedIconColor)
        ButtonState.PENDING -> Color(resolvedIconColor).copy(alpha = 0.6f)
        ButtonState.INACTIVE -> Color(resolvedIconColor).copy(alpha = 0.12f)
    }
    val tint = when (item.state) {
        ButtonState.ACTIVE, ButtonState.PENDING -> Color(resolvedBgColor)
        ButtonState.INACTIVE -> Color(resolvedIconColor)
    }
    Box(
        modifier = Modifier
            .padding(4.dp)
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(item.iconRes),
            contentDescription = item.label,
            colorFilter = ColorFilter.tint(tint),
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun MessageAncPreview(
    state: AncWidgetRenderState.Message,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(state.resolvedBgColor))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = state.primaryText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(state.resolvedTextColor),
            textAlign = TextAlign.Center,
        )
        if (state.secondaryText != null) {
            Text(
                text = state.secondaryText,
                fontSize = 12.sp,
                color = Color(state.resolvedTextColor),
                textAlign = TextAlign.Center,
            )
        }
    }
}
