package eu.darken.capod.common.theming

import androidx.annotation.StringRes
import eu.darken.capod.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ThemeColor(
    @StringRes val labelRes: Int
) {
    @SerialName("theme.color.blue") BLUE(R.string.ui_theme_color_blue_label),
    @SerialName("theme.color.green") GREEN(R.string.ui_theme_color_green_label),
    @SerialName("theme.color.amber") AMBER(R.string.ui_theme_color_amber_label),
}
