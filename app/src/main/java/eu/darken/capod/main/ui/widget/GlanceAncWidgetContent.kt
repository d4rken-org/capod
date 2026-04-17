package eu.darken.capod.main.ui.widget

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import eu.darken.capod.main.ui.MainActivity

@Composable
fun GlanceAncWidgetContent(
    state: AncWidgetRenderState,
    context: android.content.Context,
    appWidgetId: Int,
) {
    val openApp = actionStartActivity(
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    )

    when (state) {
        is AncWidgetRenderState.Active -> GlanceAncActive(state, appWidgetId)
        is AncWidgetRenderState.Message -> GlanceAncMessage(state, GlanceModifier.clickable(openApp))
    }
}

@Composable
private fun GlanceAncActive(
    state: AncWidgetRenderState.Active,
    appWidgetId: Int,
) {
    val textColor = fixedAncColor(state.resolvedTextColor)
    val iconColor = fixedAncColor(state.resolvedIconColor)
    val showDeviceLabel = state.layout != AncLayout.ROW_ICONS
            && state.layout != AncLayout.COLUMN_ICONS
            && state.layout != AncLayout.QUAD_CORNERS

    AncWidgetRoot(state.resolvedBgColor) {
        when (state.layout) {
            AncLayout.GRID_2X2 -> AncGrid(state.modes, iconColor, state.resolvedIconColor, state.resolvedActiveColor, state.resolvedOnActiveColor, appWidgetId)
            AncLayout.ROW -> AncRow(state.modes, iconColor, textColor, state.resolvedIconColor, state.resolvedActiveColor, state.resolvedOnActiveColor, appWidgetId)
            AncLayout.COLUMN -> AncColumn(state.modes, iconColor, textColor, state.resolvedIconColor, state.resolvedActiveColor, state.resolvedOnActiveColor, appWidgetId)
            AncLayout.ROW_ICONS -> AncRowIcons(state.modes, state.resolvedIconColor, state.resolvedActiveColor, state.resolvedOnActiveColor, appWidgetId)
            AncLayout.COLUMN_ICONS -> AncColumnIcons(state.modes, state.resolvedIconColor, state.resolvedActiveColor, state.resolvedOnActiveColor, appWidgetId)
            AncLayout.QUAD_CORNERS -> AncQuadCorners(state.modes, state.resolvedIconColor, state.resolvedActiveColor, state.resolvedOnActiveColor, appWidgetId)
        }
        if (showDeviceLabel) {
            GlanceAncDeviceLabel(state.deviceLabel, state.theme.showDeviceLabel, state.resolvedTextColor)
        }
    }
}

@Composable
private fun AncRowIcons(
    modes: List<ModeItem>,
    resolvedIconColor: Int,
    resolvedActiveColor: Int,
    resolvedOnActiveColor: Int,
    appWidgetId: Int,
) {
    val dividerColor = fixedAncColor(applyAncAlpha(resolvedIconColor, 60))
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        modes.forEachIndexed { index, item ->
            if (index > 0) {
                Spacer(modifier = GlanceModifier.width(1.dp).fillMaxHeight().background(dividerColor))
            }
            DividerRowCell(item, resolvedIconColor, resolvedActiveColor, resolvedOnActiveColor, appWidgetId)
        }
    }
}

@Composable
private fun AncColumnIcons(
    modes: List<ModeItem>,
    resolvedIconColor: Int,
    resolvedActiveColor: Int,
    resolvedOnActiveColor: Int,
    appWidgetId: Int,
) {
    val dividerColor = fixedAncColor(applyAncAlpha(resolvedIconColor, 60))
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        modes.forEachIndexed { index, item ->
            if (index > 0) {
                Spacer(modifier = GlanceModifier.fillMaxWidth().height(1.dp).background(dividerColor))
            }
            DividerColumnCell(item, resolvedIconColor, resolvedActiveColor, resolvedOnActiveColor, appWidgetId)
        }
    }
}

@Composable
private fun RowScope.DividerRowCell(
    item: ModeItem,
    resolvedIconColor: Int,
    resolvedActiveColor: Int,
    resolvedOnActiveColor: Int,
    appWidgetId: Int,
) {
    Box(
        modifier = GlanceModifier
            .defaultWeight()
            .fillMaxHeight()
            .clickable(ancModeAction(item, appWidgetId)),
        contentAlignment = Alignment.Center,
    ) {
        DividerCellIcon(item, resolvedIconColor, resolvedActiveColor, resolvedOnActiveColor)
    }
}

@Composable
private fun ColumnScope.DividerColumnCell(
    item: ModeItem,
    resolvedIconColor: Int,
    resolvedActiveColor: Int,
    resolvedOnActiveColor: Int,
    appWidgetId: Int,
) {
    Box(
        modifier = GlanceModifier
            .defaultWeight()
            .fillMaxWidth()
            .clickable(ancModeAction(item, appWidgetId)),
        contentAlignment = Alignment.Center,
    ) {
        DividerCellIcon(item, resolvedIconColor, resolvedActiveColor, resolvedOnActiveColor)
    }
}

@Composable
private fun DividerCellIcon(
    item: ModeItem,
    resolvedIconColor: Int,
    resolvedActiveColor: Int,
    resolvedOnActiveColor: Int,
) {
    val bgModifier = when (item.state) {
        ButtonState.ACTIVE -> GlanceModifier.background(fixedAncColor(resolvedActiveColor))
        ButtonState.PENDING -> GlanceModifier.background(fixedAncColor(applyAncAlpha(resolvedActiveColor, 153)))
        ButtonState.INACTIVE -> GlanceModifier
    }
    val tint = when (item.state) {
        ButtonState.ACTIVE, ButtonState.PENDING -> ColorFilter.tint(fixedAncColor(resolvedOnActiveColor))
        ButtonState.INACTIVE -> ColorFilter.tint(fixedAncColor(applyAncAlpha(resolvedIconColor, 160)))
    }
    Box(
        modifier = bgModifier
            .size(36.dp)
            .cornerRadius(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(item.iconRes),
            contentDescription = item.label,
            modifier = GlanceModifier.size(22.dp),
            colorFilter = tint,
        )
    }
}

@Composable
private fun AncQuadCorners(
    modes: List<ModeItem>,
    resolvedIconColor: Int,
    resolvedActiveColor: Int,
    resolvedOnActiveColor: Int,
    appWidgetId: Int,
) {
    val dividerColor = fixedAncColor(applyAncAlpha(resolvedIconColor, 60))

    Column(modifier = GlanceModifier.fillMaxSize()) {
        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            QuadCornerCell(modes.getOrNull(0), resolvedIconColor, resolvedActiveColor, resolvedOnActiveColor, appWidgetId)
            Spacer(modifier = GlanceModifier.width(1.dp).fillMaxHeight().background(dividerColor))
            QuadCornerCell(modes.getOrNull(1), resolvedIconColor, resolvedActiveColor, resolvedOnActiveColor, appWidgetId)
        }
        Spacer(modifier = GlanceModifier.fillMaxWidth().height(1.dp).background(dividerColor))
        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            QuadCornerCell(modes.getOrNull(2), resolvedIconColor, resolvedActiveColor, resolvedOnActiveColor, appWidgetId)
            Spacer(modifier = GlanceModifier.width(1.dp).fillMaxHeight().background(dividerColor))
            QuadCornerCell(modes.getOrNull(3), resolvedIconColor, resolvedActiveColor, resolvedOnActiveColor, appWidgetId)
        }
    }
}

@Composable
private fun RowScope.QuadCornerCell(
    item: ModeItem?,
    resolvedIconColor: Int,
    resolvedActiveColor: Int,
    resolvedOnActiveColor: Int,
    appWidgetId: Int,
) {
    val cellModifier = GlanceModifier.defaultWeight().fillMaxHeight()
    val clickable = if (item != null) cellModifier.clickable(ancModeAction(item, appWidgetId)) else cellModifier
    Box(
        modifier = clickable,
        contentAlignment = Alignment.Center,
    ) {
        if (item != null) {
            DividerCellIcon(item, resolvedIconColor, resolvedActiveColor, resolvedOnActiveColor)
        }
    }
}

@Composable
private fun AncGrid(
    modes: List<ModeItem>,
    iconColor: ColorProvider,
    resolvedIconColor: Int,
    resolvedActiveColor: Int,
    resolvedOnActiveColor: Int,
    appWidgetId: Int,
) {
    val firstRow = modes.take(2)
    val secondRow = modes.drop(2)

    Column(
        modifier = GlanceModifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            firstRow.forEachIndexed { index, item ->
                if (index > 0) Spacer(modifier = GlanceModifier.width(10.dp))
                AncGridButton(item, iconColor, resolvedIconColor, resolvedActiveColor, resolvedOnActiveColor, appWidgetId)
            }
        }
        if (secondRow.isNotEmpty()) {
            Spacer(modifier = GlanceModifier.height(10.dp))
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                secondRow.forEachIndexed { index, item ->
                    if (index > 0) Spacer(modifier = GlanceModifier.width(10.dp))
                    AncGridButton(item, iconColor, resolvedIconColor, resolvedActiveColor, resolvedOnActiveColor, appWidgetId)
                }
            }
        }
    }
}

@Composable
private fun AncGridButton(
    item: ModeItem,
    iconColor: ColorProvider,
    resolvedIconColor: Int,
    resolvedActiveColor: Int,
    resolvedOnActiveColor: Int,
    appWidgetId: Int,
) {
    val bgModifier = when (item.state) {
        ButtonState.ACTIVE -> GlanceModifier.background(fixedAncColor(resolvedActiveColor))
        ButtonState.PENDING -> GlanceModifier.background(fixedAncColor(applyAncAlpha(resolvedActiveColor, 153)))
        ButtonState.INACTIVE -> GlanceModifier.background(fixedAncColor(applyAncAlpha(resolvedIconColor, 30)))
    }
    val tint = when (item.state) {
        ButtonState.ACTIVE -> ColorFilter.tint(fixedAncColor(resolvedOnActiveColor))
        ButtonState.PENDING -> ColorFilter.tint(fixedAncColor(resolvedOnActiveColor))
        ButtonState.INACTIVE -> ColorFilter.tint(iconColor)
    }

    Box(
        modifier = bgModifier
            .padding(4.dp)
            .size(48.dp)
            .cornerRadius(12.dp)
            .clickable(ancModeAction(item, appWidgetId)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(item.iconRes),
            contentDescription = item.label,
            modifier = GlanceModifier.size(24.dp),
            colorFilter = tint,
        )
    }
}

@Composable
private fun AncRow(
    modes: List<ModeItem>,
    iconColor: ColorProvider,
    textColor: ColorProvider,
    resolvedIconColor: Int,
    resolvedActiveColor: Int,
    resolvedOnActiveColor: Int,
    appWidgetId: Int,
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        modes.forEachIndexed { index, item ->
            if (index > 0) Spacer(modifier = GlanceModifier.width(6.dp))
            AncLabeledButton(item, iconColor, textColor, resolvedIconColor, resolvedActiveColor, resolvedOnActiveColor, appWidgetId)
        }
    }
}

@Composable
private fun AncColumn(
    modes: List<ModeItem>,
    iconColor: ColorProvider,
    textColor: ColorProvider,
    resolvedIconColor: Int,
    resolvedActiveColor: Int,
    resolvedOnActiveColor: Int,
    appWidgetId: Int,
) {
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        modes.forEachIndexed { index, item ->
            if (index > 0) Spacer(modifier = GlanceModifier.height(6.dp))
            AncColumnItem(item, iconColor, textColor, resolvedIconColor, resolvedActiveColor, resolvedOnActiveColor, appWidgetId)
        }
    }
}

@Composable
private fun ColumnScope.AncColumnItem(
    item: ModeItem,
    iconColor: ColorProvider,
    textColor: ColorProvider,
    resolvedIconColor: Int,
    resolvedActiveColor: Int,
    resolvedOnActiveColor: Int,
    appWidgetId: Int,
) {
    val bgModifier = when (item.state) {
        ButtonState.ACTIVE -> GlanceModifier.background(fixedAncColor(resolvedActiveColor))
        ButtonState.PENDING -> GlanceModifier.background(fixedAncColor(applyAncAlpha(resolvedActiveColor, 153)))
        ButtonState.INACTIVE -> GlanceModifier.background(fixedAncColor(applyAncAlpha(resolvedIconColor, 30)))
    }
    val tint = when (item.state) {
        ButtonState.ACTIVE, ButtonState.PENDING -> ColorFilter.tint(fixedAncColor(resolvedOnActiveColor))
        ButtonState.INACTIVE -> ColorFilter.tint(iconColor)
    }
    val labelColor = when (item.state) {
        ButtonState.ACTIVE, ButtonState.PENDING -> fixedAncColor(resolvedOnActiveColor)
        ButtonState.INACTIVE -> textColor
    }
    Row(
        modifier = bgModifier
            .fillMaxWidth()
            .defaultWeight()
            .cornerRadius(12.dp)
            .clickable(ancModeAction(item, appWidgetId))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(item.iconRes),
            contentDescription = null,
            modifier = GlanceModifier.size(18.dp),
            colorFilter = tint,
        )
        Spacer(modifier = GlanceModifier.width(6.dp))
        Text(
            text = item.label,
            style = TextStyle(color = labelColor, fontSize = 12.sp, fontWeight = FontWeight.Medium),
        )
    }
}

@Composable
private fun AncLabeledButton(
    item: ModeItem,
    iconColor: ColorProvider,
    textColor: ColorProvider,
    resolvedIconColor: Int,
    resolvedActiveColor: Int,
    resolvedOnActiveColor: Int,
    appWidgetId: Int,
    horizontal: Boolean = false,
) {
    val bgModifier = when (item.state) {
        ButtonState.ACTIVE -> GlanceModifier.background(fixedAncColor(resolvedActiveColor))
        ButtonState.PENDING -> GlanceModifier.background(fixedAncColor(applyAncAlpha(resolvedActiveColor, 153)))
        ButtonState.INACTIVE -> GlanceModifier.background(fixedAncColor(applyAncAlpha(resolvedIconColor, 30)))
    }
    val tint = when (item.state) {
        ButtonState.ACTIVE -> ColorFilter.tint(fixedAncColor(resolvedOnActiveColor))
        ButtonState.PENDING -> ColorFilter.tint(fixedAncColor(resolvedOnActiveColor))
        ButtonState.INACTIVE -> ColorFilter.tint(iconColor)
    }
    val labelColor = when (item.state) {
        ButtonState.ACTIVE -> fixedAncColor(resolvedOnActiveColor)
        ButtonState.PENDING -> fixedAncColor(resolvedOnActiveColor)
        ButtonState.INACTIVE -> textColor
    }

    if (horizontal) {
        Row(
            modifier = bgModifier
                .fillMaxWidth()
                .cornerRadius(12.dp)
                .clickable(ancModeAction(item, appWidgetId))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                provider = ImageProvider(item.iconRes),
                contentDescription = null,
                modifier = GlanceModifier.size(18.dp),
                colorFilter = tint,
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
            Text(
                text = item.label,
                style = TextStyle(color = labelColor, fontSize = 12.sp, fontWeight = FontWeight.Medium),
            )
        }
    } else {
        Column(
            modifier = bgModifier
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .cornerRadius(14.dp)
                .clickable(ancModeAction(item, appWidgetId)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                provider = ImageProvider(item.iconRes),
                contentDescription = null,
                modifier = GlanceModifier.size(22.dp),
                colorFilter = tint,
            )
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = item.label,
                style = TextStyle(color = labelColor, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun GlanceAncMessage(
    state: AncWidgetRenderState.Message,
    clickModifier: GlanceModifier,
) {
    val textStyle = TextStyle(
        color = fixedAncColor(state.resolvedTextColor),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )

    AncWidgetRoot(state.resolvedBgColor, clickModifier) {
        Text(
            text = state.primaryText,
            style = textStyle,
            modifier = GlanceModifier.fillMaxWidth(),
        )
        if (state.secondaryText != null) {
            Text(
                text = state.secondaryText,
                style = TextStyle(
                    color = fixedAncColor(state.resolvedTextColor),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                ),
                modifier = GlanceModifier.fillMaxWidth(),
                maxLines = 4,
            )
        }
    }
}

@Composable
private fun AncWidgetRoot(
    bgColor: Int,
    extraModifier: GlanceModifier = GlanceModifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = extraModifier
            .fillMaxSize()
            .background(fixedAncColor(bgColor))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

@Composable
private fun GlanceAncDeviceLabel(
    label: String?,
    visible: Boolean,
    textColor: Int,
) {
    if (visible && label != null) {
        Text(
            text = label,
            style = TextStyle(
                color = fixedAncColor(textColor),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
            modifier = GlanceModifier.padding(top = 8.dp),
        )
    }
}

private fun fixedAncColor(argb: Int): ColorProvider = ColorProvider(Color(argb))

private fun applyAncAlpha(color: Int, alpha: Int): Int {
    return android.graphics.Color.argb(
        alpha,
        android.graphics.Color.red(color),
        android.graphics.Color.green(color),
        android.graphics.Color.blue(color),
    )
}

private fun ancModeAction(item: ModeItem, appWidgetId: Int) = actionRunCallback<AncModeActionCallback>(
    parameters = androidx.glance.action.actionParametersOf(
        AncModeActionCallback.ANC_MODE_KEY to item.mode.name,
        AncModeActionCallback.WIDGET_ID_KEY to appWidgetId,
    )
)
