package eu.darken.capod.main.ui.widget

import android.content.Context
import androidx.annotation.ColorInt
import androidx.appcompat.view.ContextThemeWrapper
import eu.darken.capod.R
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.ble.getBatteryDrawable
import eu.darken.capod.pods.core.apple.ble.toBatteryFloat

object WidgetRenderStateMapper {

    fun map(
        context: Context,
        device: PodDevice?,
        theme: WidgetTheme,
        isPro: Boolean,
        hasConfiguredProfile: Boolean,
        profileLabel: String?,
        isWide: Boolean = false,
    ): WidgetRenderState {
        val bgColor = resolvedBgColor(context, theme)
        val textColor = resolvedTextColor(context, theme)
        val iconColor = resolvedIconColor(context, theme)

        return when {
            !isPro -> WidgetRenderState.Message(
                theme = theme,
                resolvedBgColor = bgColor,
                resolvedTextColor = textColor,
                resolvedIconColor = iconColor,
                primaryText = context.getString(R.string.upgrade_capod_label),
                secondaryText = context.getString(R.string.upgrade_capod_description),
            )

            device != null && device.hasDualPods -> WidgetRenderState.DualPod(
                theme = theme,
                resolvedBgColor = bgColor,
                resolvedTextColor = textColor,
                resolvedIconColor = iconColor,
                isWide = isWide,
                deviceLabel = profileLabel ?: device.getLabel(context),
                leftIcon = device.leftPodIcon,
                leftPercent = device.batteryLeft.toBatteryFloat(),
                leftCharging = device.isLeftPodCharging == true,
                leftInEar = device.isLeftInEar == true,
                rightIcon = device.rightPodIcon,
                rightPercent = device.batteryRight.toBatteryFloat(),
                rightCharging = device.isRightPodCharging == true,
                rightInEar = device.isRightInEar == true,
                caseIcon = device.caseIcon,
                casePercent = device.batteryCase.toBatteryFloat(),
                caseCharging = device.isCaseCharging == true,
            )

            device != null && device.model != PodModel.UNKNOWN -> WidgetRenderState.SinglePod(
                theme = theme,
                resolvedBgColor = bgColor,
                resolvedTextColor = textColor,
                resolvedIconColor = iconColor,
                deviceLabel = profileLabel ?: device.getLabel(context),
                headsetIcon = device.iconRes,
                percent = device.batteryHeadset.toBatteryFloat(),
                batteryIcon = getBatteryDrawable(device.batteryHeadset),
                charging = device.isHeadsetBeingCharged == true,
                worn = device.isBeingWorn == true,
            )

            device != null -> WidgetRenderState.Message(
                theme = theme,
                resolvedBgColor = bgColor,
                resolvedTextColor = textColor,
                resolvedIconColor = iconColor,
                primaryText = context.getString(R.string.pods_unknown_label),
            )

            else -> {
                val messageRes = if (hasConfiguredProfile) {
                    R.string.widget_no_data_label
                } else {
                    R.string.overview_nomaindevice_label
                }
                WidgetRenderState.Message(
                    theme = theme,
                    resolvedBgColor = bgColor,
                    resolvedTextColor = textColor,
                    resolvedIconColor = iconColor,
                    primaryText = context.getString(messageRes),
                )
            }
        }
    }

    @ColorInt
    fun resolvedBgColor(context: Context, theme: WidgetTheme): Int {
        val bgColor = theme.backgroundColor
        return if (bgColor != null) {
            WidgetTheme.applyAlpha(bgColor, theme.backgroundAlpha)
        } else {
            resolveThemeColor(context, android.R.attr.colorBackground)
        }
    }

    @ColorInt
    fun resolvedTextColor(context: Context, theme: WidgetTheme): Int {
        return theme.foregroundColor ?: resolveThemeColor(context, android.R.attr.textColorPrimary)
    }

    @ColorInt
    fun resolvedIconColor(context: Context, theme: WidgetTheme): Int {
        return theme.foregroundColor ?: resolveThemeColor(context, android.R.attr.colorAccent)
    }

    @ColorInt
    fun resolveThemeColor(context: Context, attr: Int): Int {
        val themedContext = ContextThemeWrapper(
            context,
            com.google.android.material.R.style.Theme_Material3_DynamicColors_DayNight,
        )
        val typedArray = themedContext.theme.obtainStyledAttributes(intArrayOf(attr))
        val color = typedArray.getColor(0, android.graphics.Color.BLACK)
        typedArray.recycle()
        return color
    }
}
