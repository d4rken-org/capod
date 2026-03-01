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
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.background
import androidx.glance.unit.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import eu.darken.capod.R
import eu.darken.capod.main.ui.MainActivity
import kotlin.math.roundToInt

@Composable
fun GlanceWidgetContent(
    state: WidgetRenderState,
    context: android.content.Context,
) {
    val openApp = actionStartActivity(
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    )

    when (state) {
        is WidgetRenderState.DualPod -> GlanceDualPod(state, GlanceModifier.clickable(openApp))
        is WidgetRenderState.SinglePod -> GlanceSinglePod(state, GlanceModifier.clickable(openApp))
        is WidgetRenderState.Message -> GlanceMessage(state, GlanceModifier.clickable(openApp))
        is WidgetRenderState.Loading -> GlanceLoading(state, GlanceModifier.clickable(openApp))
    }
}

@Composable
private fun GlanceDualPod(
    state: WidgetRenderState.DualPod,
    clickModifier: GlanceModifier,
) {
    val textStyle = TextStyle(
        color = fixedColor(state.resolvedTextColor),
        fontSize = 12.sp,
    )
    val iconTint = ColorFilter.tint(fixedColor(state.resolvedIconColor))

    if (state.isWide) {
        GlanceWidgetRoot(state.resolvedBgColor, clickModifier) {
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlancePodItem(state.leftIcon, state.leftPercent, state.leftCharging, state.leftInEar, textStyle, iconTint, iconSize = 40, modifier = GlanceModifier.padding(end = 12.dp))
                GlancePodItem(state.caseIcon, state.casePercent, state.caseCharging, false, textStyle, iconTint, iconSize = 40, modifier = GlanceModifier.padding(end = 12.dp))
                GlancePodItem(state.rightIcon, state.rightPercent, state.rightCharging, state.rightInEar, textStyle, iconTint, iconSize = 40)
            }
            GlanceDeviceLabel(state.deviceLabel, state.theme.showDeviceLabel, state.resolvedTextColor)
        }
    } else {
        GlanceWidgetRoot(state.resolvedBgColor, clickModifier) {
            GlancePodItem(state.leftIcon, state.leftPercent, state.leftCharging, state.leftInEar, textStyle, iconTint)
            GlancePodItem(state.rightIcon, state.rightPercent, state.rightCharging, state.rightInEar, textStyle, iconTint)
            GlancePodItem(state.caseIcon, state.casePercent, state.caseCharging, false, textStyle, iconTint)
            GlanceDeviceLabel(state.deviceLabel, state.theme.showDeviceLabel, state.resolvedTextColor)
        }
    }
}

@Composable
private fun GlanceSinglePod(
    state: WidgetRenderState.SinglePod,
    clickModifier: GlanceModifier,
) {
    val textStyle = TextStyle(
        color = fixedColor(state.resolvedTextColor),
        fontSize = 12.sp,
    )
    val iconTint = ColorFilter.tint(fixedColor(state.resolvedIconColor))

    GlanceWidgetRoot(state.resolvedBgColor, clickModifier) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                provider = ImageProvider(state.batteryIcon),
                contentDescription = null,
                modifier = GlanceModifier.size(20.dp),
                colorFilter = iconTint,
            )
            Text(
                text = formatGlancePercent(state.percent),
                style = textStyle,
                modifier = GlanceModifier.padding(horizontal = 8.dp),
            )
            if (state.charging) {
                Image(
                    provider = ImageProvider(R.drawable.ic_baseline_power_24),
                    contentDescription = null,
                    modifier = GlanceModifier.size(20.dp),
                    colorFilter = iconTint,
                )
            }
            if (state.worn) {
                Image(
                    provider = ImageProvider(R.drawable.ic_baseline_hearing_24),
                    contentDescription = null,
                    modifier = GlanceModifier.size(20.dp),
                    colorFilter = iconTint,
                )
            }
        }
        GlanceDeviceLabel(state.deviceLabel, state.theme.showDeviceLabel, state.resolvedTextColor)
    }
}

@Composable
private fun GlanceMessage(
    state: WidgetRenderState.Message,
    clickModifier: GlanceModifier,
) {
    val textStyle = TextStyle(
        color = fixedColor(state.resolvedTextColor),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )

    GlanceWidgetRoot(state.resolvedBgColor, clickModifier) {
        Text(
            text = state.primaryText,
            style = textStyle,
            modifier = GlanceModifier.fillMaxWidth(),
        )
        if (state.secondaryText != null) {
            Text(
                text = state.secondaryText,
                style = TextStyle(
                    color = fixedColor(state.resolvedTextColor),
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
private fun GlanceLoading(
    state: WidgetRenderState.Loading,
    clickModifier: GlanceModifier,
) {
    GlanceWidgetRoot(state.resolvedBgColor, clickModifier) {
        Text(
            text = "…",
            style = TextStyle(
                color = fixedColor(state.resolvedTextColor),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
            modifier = GlanceModifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun GlanceWidgetRoot(
    bgColor: Int,
    clickModifier: GlanceModifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = clickModifier
            .fillMaxSize()
            .background(fixedColor(bgColor))
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

@Composable
private fun GlancePodItem(
    icon: Int,
    percent: Float?,
    charging: Boolean,
    inEar: Boolean,
    textStyle: TextStyle,
    iconTint: ColorFilter,
    iconSize: Int = 20,
    modifier: GlanceModifier = GlanceModifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = null,
            modifier = GlanceModifier.size(iconSize.dp),
            colorFilter = iconTint,
        )
        Text(
            text = formatGlancePercent(percent),
            style = textStyle,
            modifier = GlanceModifier.padding(horizontal = 4.dp),
        )
        if (charging) {
            Image(
                provider = ImageProvider(R.drawable.ic_baseline_power_24),
                contentDescription = null,
                modifier = GlanceModifier.size(20.dp),
                colorFilter = iconTint,
            )
        }
        if (inEar) {
            Image(
                provider = ImageProvider(R.drawable.ic_baseline_hearing_24),
                contentDescription = null,
                modifier = GlanceModifier.size(20.dp),
                colorFilter = iconTint,
            )
        }
    }
}

@Composable
private fun GlanceDeviceLabel(
    label: String?,
    visible: Boolean,
    textColor: Int,
) {
    if (visible && label != null) {
        Text(
            text = label,
            style = TextStyle(
                color = fixedColor(textColor),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

private fun fixedColor(argb: Int): ColorProvider = ColorProvider(Color(argb))

private fun formatGlancePercent(percent: Float?): String {
    return percent?.let { "${(it * 100).roundToInt()}%" } ?: "—"
}
