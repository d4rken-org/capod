package eu.darken.capod.main.ui.widget

import android.graphics.Color
import android.os.Bundle
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils

data class WidgetTheme(
    @ColorInt val backgroundColor: Int? = null,
    @ColorInt val foregroundColor: Int? = null,
    val backgroundAlpha: Int = 255,
    val showDeviceLabel: Boolean = true,
) {

    enum class Preset(
        @ColorInt val presetBg: Int?,
        @ColorInt val presetFg: Int?,
    ) {
        MATERIAL_YOU(null, null),
        CLASSIC_DARK(0xFF1E1E1E.toInt(), 0xFFFFFFFF.toInt()),
        CLASSIC_LIGHT(0xFFFFFFFF.toInt(), 0xFF1E1E1E.toInt()),
        BLUE(0xFF1565C0.toInt(), 0xFFFFFFFF.toInt()),
        GREEN(0xFF2E7D32.toInt(), 0xFFFFFFFF.toInt()),
        RED(0xFFC62828.toInt(), 0xFFFFFFFF.toInt()),
    }

    companion object {
        private const val KEY_THEME_MODE = "capod_theme_mode"
        private const val KEY_CUSTOM_BG = "capod_custom_bg"
        private const val KEY_CUSTOM_FG = "capod_custom_fg"
        private const val KEY_BG_ALPHA = "capod_bg_alpha"
        private const val KEY_SHOW_LABEL = "capod_show_label"

        private const val MODE_MATERIAL_YOU = "material_you"
        private const val MODE_CUSTOM = "custom"

        val DEFAULT = WidgetTheme()

        fun fromBundle(bundle: Bundle): WidgetTheme {
            val mode = bundle.getString(KEY_THEME_MODE, MODE_MATERIAL_YOU)
            val showLabel = bundle.getBoolean(KEY_SHOW_LABEL, true)
            val alpha = bundle.getInt(KEY_BG_ALPHA, 255).coerceIn(0, 255)
            return if (mode == MODE_CUSTOM) {
                WidgetTheme(
                    backgroundColor = if (bundle.containsKey(KEY_CUSTOM_BG)) bundle.getInt(KEY_CUSTOM_BG) else null,
                    foregroundColor = if (bundle.containsKey(KEY_CUSTOM_FG)) bundle.getInt(KEY_CUSTOM_FG) else null,
                    backgroundAlpha = alpha,
                    showDeviceLabel = showLabel,
                )
            } else {
                WidgetTheme(
                    backgroundAlpha = alpha,
                    showDeviceLabel = showLabel,
                )
            }
        }

        fun applyAlpha(@ColorInt color: Int, alpha: Int): Int {
            val clampedAlpha = alpha.coerceIn(0, 255)
            return Color.argb(clampedAlpha, Color.red(color), Color.green(color), Color.blue(color))
        }

        fun bestContrastForeground(@ColorInt bgColor: Int): Int {
            val whiteContrast = ColorUtils.calculateContrast(Color.WHITE, bgColor)
            val blackContrast = ColorUtils.calculateContrast(Color.BLACK, bgColor)
            return if (whiteContrast >= blackContrast) Color.WHITE else Color.BLACK
        }

        fun matchPreset(theme: WidgetTheme): Preset? = Preset.entries.firstOrNull { preset ->
            preset.presetBg == theme.backgroundColor && preset.presetFg == theme.foregroundColor && theme.backgroundAlpha == 255
        }
    }

    fun toBundle(bundle: Bundle) {
        if (backgroundColor == null && foregroundColor == null) {
            bundle.putString(KEY_THEME_MODE, MODE_MATERIAL_YOU)
            bundle.remove(KEY_CUSTOM_BG)
            bundle.remove(KEY_CUSTOM_FG)
        } else {
            bundle.putString(KEY_THEME_MODE, MODE_CUSTOM)
            if (backgroundColor != null) {
                bundle.putInt(KEY_CUSTOM_BG, backgroundColor)
            } else {
                bundle.remove(KEY_CUSTOM_BG)
            }
            if (foregroundColor != null) {
                bundle.putInt(KEY_CUSTOM_FG, foregroundColor)
            } else {
                bundle.remove(KEY_CUSTOM_FG)
            }
        }
        bundle.putInt(KEY_BG_ALPHA, backgroundAlpha.coerceIn(0, 255))
        bundle.putBoolean(KEY_SHOW_LABEL, showDeviceLabel)
    }
}
