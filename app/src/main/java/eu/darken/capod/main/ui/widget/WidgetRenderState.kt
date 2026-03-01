package eu.darken.capod.main.ui.widget

import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import eu.darken.capod.R

sealed class WidgetRenderState {
    abstract val theme: WidgetTheme
    @get:ColorInt abstract val resolvedBgColor: Int
    @get:ColorInt abstract val resolvedTextColor: Int
    @get:ColorInt abstract val resolvedIconColor: Int

    data class DualPod(
        override val theme: WidgetTheme,
        @ColorInt override val resolvedBgColor: Int,
        @ColorInt override val resolvedTextColor: Int,
        @ColorInt override val resolvedIconColor: Int,
        val isWide: Boolean,
        val deviceLabel: String?,
        @DrawableRes val leftIcon: Int,
        val leftPercent: Float?,
        val leftCharging: Boolean,
        val leftInEar: Boolean,
        @DrawableRes val rightIcon: Int,
        val rightPercent: Float?,
        val rightCharging: Boolean,
        val rightInEar: Boolean,
        @DrawableRes val caseIcon: Int,
        val casePercent: Float?,
        val caseCharging: Boolean,
    ) : WidgetRenderState()

    data class SinglePod(
        override val theme: WidgetTheme,
        @ColorInt override val resolvedBgColor: Int,
        @ColorInt override val resolvedTextColor: Int,
        @ColorInt override val resolvedIconColor: Int,
        val deviceLabel: String?,
        @DrawableRes val headsetIcon: Int,
        val percent: Float?,
        @DrawableRes val batteryIcon: Int,
        val charging: Boolean,
        val worn: Boolean,
    ) : WidgetRenderState()

    data class Message(
        override val theme: WidgetTheme,
        @ColorInt override val resolvedBgColor: Int,
        @ColorInt override val resolvedTextColor: Int,
        @ColorInt override val resolvedIconColor: Int,
        val primaryText: String,
        val secondaryText: String? = null,
    ) : WidgetRenderState()

    data class Loading(
        override val theme: WidgetTheme,
        @ColorInt override val resolvedBgColor: Int,
        @ColorInt override val resolvedTextColor: Int,
        @ColorInt override val resolvedIconColor: Int,
    ) : WidgetRenderState()

    companion object {
        fun previewDualPod(
            theme: WidgetTheme = WidgetTheme.DEFAULT,
            @ColorInt bgColor: Int = 0xFFFFFFFF.toInt(),
            @ColorInt textColor: Int = 0xFF1E1E1E.toInt(),
            @ColorInt iconColor: Int = 0xFF1E1E1E.toInt(),
            isWide: Boolean = false,
        ): DualPod = DualPod(
            theme = theme,
            resolvedBgColor = bgColor,
            resolvedTextColor = textColor,
            resolvedIconColor = iconColor,
            isWide = isWide,
            deviceLabel = "My AirPods Pro",
            leftIcon = R.drawable.device_airpods_pro2_left,
            leftPercent = 0.85f,
            leftCharging = false,
            leftInEar = true,
            rightIcon = R.drawable.device_airpods_pro2_right,
            rightPercent = 0.92f,
            rightCharging = true,
            rightInEar = false,
            caseIcon = R.drawable.device_airpods_pro2_case,
            casePercent = 1.0f,
            caseCharging = false,
        )
    }
}
