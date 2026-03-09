package eu.darken.capod.common.theming

import androidx.annotation.StringRes
import eu.darken.capod.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ThemeStyle(
    @StringRes val labelRes: Int
) {
    @SerialName("theme.style.default") DEFAULT(R.string.ui_theme_style_default_label),
    @SerialName("theme.style.materialyou") MATERIAL_YOU(R.string.ui_theme_style_materialyou_label),
    @SerialName("theme.style.mediumcontrast") MEDIUM_CONTRAST(R.string.ui_theme_style_medium_contrast_label),
    @SerialName("theme.style.highcontrast") HIGH_CONTRAST(R.string.ui_theme_style_high_contrast_label),
}
