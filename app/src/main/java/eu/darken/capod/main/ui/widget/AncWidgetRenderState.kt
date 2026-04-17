package eu.darken.capod.main.ui.widget

import android.content.Context
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import eu.darken.capod.R
import eu.darken.capod.main.ui.components.shortLabel
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting

sealed class AncWidgetRenderState {
    abstract val theme: WidgetTheme
    @get:ColorInt abstract val resolvedBgColor: Int
    @get:ColorInt abstract val resolvedTextColor: Int
    @get:ColorInt abstract val resolvedIconColor: Int

    data class Active(
        override val theme: WidgetTheme,
        @ColorInt override val resolvedBgColor: Int,
        @ColorInt override val resolvedTextColor: Int,
        @ColorInt override val resolvedIconColor: Int,
        @ColorInt val resolvedActiveColor: Int,
        @ColorInt val resolvedOnActiveColor: Int,
        val modes: List<ModeItem>,
        val deviceLabel: String?,
        val layout: AncLayout,
    ) : AncWidgetRenderState()

    data class Message(
        override val theme: WidgetTheme,
        @ColorInt override val resolvedBgColor: Int,
        @ColorInt override val resolvedTextColor: Int,
        @ColorInt override val resolvedIconColor: Int,
        val primaryText: String,
        val secondaryText: String? = null,
    ) : AncWidgetRenderState()

    companion object {
        fun previewActive(
            context: Context,
            theme: WidgetTheme = WidgetTheme.DEFAULT,
            @ColorInt bgColor: Int = 0xFFFFFFFF.toInt(),
            @ColorInt textColor: Int = 0xFF1E1E1E.toInt(),
            @ColorInt iconColor: Int = 0xFF1E1E1E.toInt(),
            @ColorInt activeColor: Int = 0xFFCCE5FF.toInt(),
            @ColorInt onActiveColor: Int = 0xFF001E30.toInt(),
            layout: AncLayout = AncLayout.GRID_2X2,
            modes: List<AapSetting.AncMode.Value> = AapSetting.AncMode.Value.entries,
            activeMode: AapSetting.AncMode.Value = AapSetting.AncMode.Value.ON,
            deviceLabel: String? = null,
        ): Active = Active(
            theme = theme,
            resolvedBgColor = bgColor,
            resolvedTextColor = textColor,
            resolvedIconColor = iconColor,
            resolvedActiveColor = activeColor,
            resolvedOnActiveColor = onActiveColor,
            modes = modes.map { mode ->
                ModeItem(
                    mode = mode,
                    label = mode.shortLabel(context),
                    iconRes = mode.previewIconRes(),
                    state = if (mode == activeMode) ButtonState.ACTIVE else ButtonState.INACTIVE,
                )
            },
            deviceLabel = deviceLabel,
            layout = layout,
        )
    }
}

data class ModeItem(
    val mode: AapSetting.AncMode.Value,
    val label: String,
    @DrawableRes val iconRes: Int,
    val state: ButtonState,
)

enum class ButtonState {
    ACTIVE, PENDING, INACTIVE,
}

enum class AncLayout {
    QUAD_CORNERS, ROW_ICONS, COLUMN_ICONS, GRID_2X2, ROW, COLUMN,
}

private fun AapSetting.AncMode.Value.previewIconRes(): Int = when (this) {
    AapSetting.AncMode.Value.OFF -> R.drawable.ic_anc_off
    AapSetting.AncMode.Value.ON -> R.drawable.ic_anc_on
    AapSetting.AncMode.Value.TRANSPARENCY -> R.drawable.ic_anc_transparency
    AapSetting.AncMode.Value.ADAPTIVE -> R.drawable.ic_anc_adaptive
}
