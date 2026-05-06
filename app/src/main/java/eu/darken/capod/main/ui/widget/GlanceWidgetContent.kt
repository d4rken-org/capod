package eu.darken.capod.main.ui.widget

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
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
import eu.darken.capod.pods.core.apple.ble.isKnownBattery
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
    val iconTint = ColorFilter.tint(fixedColor(state.resolvedIconColor))

    when (state.layout) {
        BatteryLayout.WIDE -> {
            val textStyle = TextStyle(color = fixedColor(state.resolvedTextColor), fontSize = 12.sp)
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
        }

        BatteryLayout.NARROW -> {
            val textStyle = TextStyle(color = fixedColor(state.resolvedTextColor), fontSize = 12.sp)
            GlanceWidgetRoot(state.resolvedBgColor, clickModifier) {
                GlancePodItem(state.leftIcon, state.leftPercent, state.leftCharging, state.leftInEar, textStyle, iconTint)
                GlancePodItem(state.rightIcon, state.rightPercent, state.rightCharging, state.rightInEar, textStyle, iconTint)
                GlancePodItem(state.caseIcon, state.casePercent, state.caseCharging, false, textStyle, iconTint)
                GlanceDeviceLabel(state.deviceLabel, state.theme.showDeviceLabel, state.resolvedTextColor)
            }
        }

        BatteryLayout.TINY_COLUMN -> {
            val textStyle = TextStyle(color = fixedColor(state.resolvedTextColor), fontSize = 12.sp)
            GlanceWidgetRoot(
                bgColor = state.resolvedBgColor,
                clickModifier = clickModifier,
                horizontalPadding = 4.dp,
                verticalPadding = 0.dp,
            ) {
                GlanceTinyPodItem(state.leftIcon, state.leftPercent, textStyle, iconTint)
                GlanceTinyPodItem(state.rightIcon, state.rightPercent, textStyle, iconTint)
                GlanceTinyPodItem(state.caseIcon, state.casePercent, textStyle, iconTint)
            }
        }
    }
}

@Composable
private fun GlanceSinglePod(
    state: WidgetRenderState.SinglePod,
    clickModifier: GlanceModifier,
) {
    val iconTint = ColorFilter.tint(fixedColor(state.resolvedIconColor))

    when (state.layout) {
        BatteryLayout.WIDE, BatteryLayout.NARROW -> {
            val textStyle = TextStyle(color = fixedColor(state.resolvedTextColor), fontSize = 12.sp)
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

        BatteryLayout.TINY_COLUMN -> {
            val textStyle = TextStyle(color = fixedColor(state.resolvedTextColor), fontSize = 12.sp)
            GlanceWidgetRoot(
                bgColor = state.resolvedBgColor,
                clickModifier = clickModifier,
                horizontalPadding = 4.dp,
                verticalPadding = 0.dp,
            ) {
                GlanceTinyPodItem(state.batteryIcon, state.percent, textStyle, iconTint)
            }
        }
    }
}

@Composable
private fun GlanceMessage(
    state: WidgetRenderState.Message,
    clickModifier: GlanceModifier,
) {
    val isCompact = state.layout == BatteryLayout.TINY_COLUMN
    val fontSize = if (isCompact) 10.sp else 12.sp
    val hPad = if (isCompact) 4.dp else 16.dp
    val vPad = if (isCompact) 0.dp else 4.dp

    GlanceWidgetRoot(
        bgColor = state.resolvedBgColor,
        clickModifier = clickModifier,
        horizontalPadding = hPad,
        verticalPadding = vPad,
    ) {
        Text(
            text = state.primaryText,
            style = TextStyle(
                color = fixedColor(state.resolvedTextColor),
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
            modifier = GlanceModifier.fillMaxWidth(),
            maxLines = if (isCompact) 2 else Int.MAX_VALUE,
        )
        if (!isCompact && state.secondaryText != null) {
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
    val isCompact = state.layout == BatteryLayout.TINY_COLUMN
    GlanceWidgetRoot(
        bgColor = state.resolvedBgColor,
        clickModifier = clickModifier,
        horizontalPadding = if (isCompact) 4.dp else 16.dp,
        verticalPadding = if (isCompact) 0.dp else 4.dp,
    ) {
        Text(
            text = "…",
            style = TextStyle(
                color = fixedColor(state.resolvedTextColor),
                fontSize = if (isCompact) 10.sp else 12.sp,
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
    horizontalPadding: Dp = 16.dp,
    verticalPadding: Dp = 4.dp,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = clickModifier
            .fillMaxSize()
            .background(fixedColor(bgColor))
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

@Composable
private fun GlancePodItem(
    icon: Int,
    percent: Float,
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
private fun GlanceTinyPodItem(
    icon: Int,
    percent: Float,
    textStyle: TextStyle,
    iconTint: ColorFilter,
    modifier: GlanceModifier = GlanceModifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = null,
            modifier = GlanceModifier.size(14.dp),
            colorFilter = iconTint,
        )
        Text(
            text = formatGlancePercent(percent),
            style = textStyle,
            maxLines = 1,
            modifier = GlanceModifier.padding(start = 4.dp),
        )
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

private fun formatGlancePercent(percent: Float): String =
    if (isKnownBattery(percent)) "${(percent * 100).roundToInt()}%" else "—"
