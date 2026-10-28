package eu.darken.capod.main.ui.widget

import android.content.Context
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import eu.darken.capod.R
import eu.darken.capod.pods.core.apple.ble.formatBatteryDurationShort

enum class BatteryLayout {
    TINY_COLUMN,
    NARROW,
    WIDE;

    companion object {
        fun forCells(widthCells: Int): BatteryLayout = when {
            widthCells <= 1 -> TINY_COLUMN
            widthCells >= 5 -> WIDE
            else -> NARROW
        }
    }
}

sealed class WidgetRenderState {
    abstract val theme: WidgetTheme
    @get:ColorInt abstract val resolvedBgColor: Int
    @get:ColorInt abstract val resolvedTextColor: Int
    @get:ColorInt abstract val resolvedIconColor: Int
    abstract val layout: BatteryLayout

    data class DualPod(
        override val theme: WidgetTheme,
        @ColorInt override val resolvedBgColor: Int,
        @ColorInt override val resolvedTextColor: Int,
        @ColorInt override val resolvedIconColor: Int,
        override val layout: BatteryLayout,
        val deviceLabel: String?,
        @DrawableRes val leftIcon: Int,
        val leftPercent: Float,
        val leftCharging: Boolean,
        val leftInEar: Boolean,
        val leftEstimate: String?,
        @DrawableRes val rightIcon: Int,
        val rightPercent: Float,
        val rightCharging: Boolean,
        val rightInEar: Boolean,
        val rightEstimate: String?,
        @DrawableRes val caseIcon: Int,
        val casePercent: Float,
        val caseCharging: Boolean,
    ) : WidgetRenderState()

    data class SinglePod(
        override val theme: WidgetTheme,
        @ColorInt override val resolvedBgColor: Int,
        @ColorInt override val resolvedTextColor: Int,
        @ColorInt override val resolvedIconColor: Int,
        override val layout: BatteryLayout,
        val deviceLabel: String?,
        @DrawableRes val headsetIcon: Int,
        val percent: Float,
        @DrawableRes val batteryIcon: Int,
        val charging: Boolean,
        val worn: Boolean,
        val estimate: String?,
    ) : WidgetRenderState()

    data class Message(
        override val theme: WidgetTheme,
        @ColorInt override val resolvedBgColor: Int,
        @ColorInt override val resolvedTextColor: Int,
        @ColorInt override val resolvedIconColor: Int,
        override val layout: BatteryLayout,
        val primaryText: String,
        val secondaryText: String? = null,
    ) : WidgetRenderState()

    data class Loading(
        override val theme: WidgetTheme,
        @ColorInt override val resolvedBgColor: Int,
        @ColorInt override val resolvedTextColor: Int,
        @ColorInt override val resolvedIconColor: Int,
        override val layout: BatteryLayout,
    ) : WidgetRenderState()

    companion object {
        fun previewDualPod(
            theme: WidgetTheme = WidgetTheme.DEFAULT,
            @ColorInt bgColor: Int = 0xFFFFFFFF.toInt(),
            @ColorInt textColor: Int = 0xFF1E1E1E.toInt(),
            @ColorInt iconColor: Int = 0xFF1E1E1E.toInt(),
            layout: BatteryLayout = BatteryLayout.NARROW,
        ): DualPod = DualPod(
            theme = theme,
            resolvedBgColor = bgColor,
            resolvedTextColor = textColor,
            resolvedIconColor = iconColor,
            layout = layout,
            deviceLabel = "My AirPods Pro",
            leftIcon = R.drawable.device_airpods_pro2_left,
            leftPercent = 0.85f,
            leftCharging = false,
            leftInEar = true,
            leftEstimate = "4h 55m",
            rightIcon = R.drawable.device_airpods_pro2_right,
            rightPercent = 0.92f,
            rightCharging = true,
            rightInEar = false,
            rightEstimate = "25m",
            caseIcon = R.drawable.device_airpods_pro2_case,
            casePercent = 1.0f,
            caseCharging = false,
        )

        fun previewSinglePod(
            theme: WidgetTheme = WidgetTheme.DEFAULT,
            @ColorInt bgColor: Int = 0xFFFFFFFF.toInt(),
            @ColorInt textColor: Int = 0xFF1E1E1E.toInt(),
            @ColorInt iconColor: Int = 0xFF1E1E1E.toInt(),
            layout: BatteryLayout = BatteryLayout.NARROW,
        ): SinglePod = SinglePod(
            theme = theme,
            resolvedBgColor = bgColor,
            resolvedTextColor = textColor,
            resolvedIconColor = iconColor,
            layout = layout,
            deviceLabel = "My AirPods Max",
            headsetIcon = R.drawable.device_airpods_max,
            percent = 0.72f,
            batteryIcon = R.drawable.ic_baseline_battery_3_bar_24,
            charging = false,
            worn = true,
            estimate = "14h 30m",
        )
    }
}

/**
 * Replaces the preview factory's hardcoded sample estimates with properly localized ones for
 * user-facing preview surfaces (widget picker, configuration screen, store screenshots).
 */
fun WidgetRenderState.DualPod.withLocalizedPreviewEstimates(context: Context): WidgetRenderState.DualPod = copy(
    leftEstimate = formatBatteryDurationShort(context, 295),
    rightEstimate = formatBatteryDurationShort(context, 25),
)
