package eu.darken.capod.common.theming

import androidx.annotation.StringRes
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import eu.darken.capod.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@JsonClass(generateAdapter = false)
enum class ThemeColor(
    @StringRes val labelRes: Int
) {
    @SerialName("theme.color.blue") @Json(name = "theme.color.blue") BLUE(R.string.ui_theme_color_blue_label),
    @SerialName("theme.color.green") @Json(name = "theme.color.green") GREEN(R.string.ui_theme_color_green_label),
    @SerialName("theme.color.amber") @Json(name = "theme.color.amber") AMBER(R.string.ui_theme_color_amber_label),
}
