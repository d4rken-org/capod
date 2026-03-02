package eu.darken.capod.common.theming

import androidx.annotation.StringRes
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import eu.darken.capod.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@JsonClass(generateAdapter = false)
enum class ThemeStyle(
    @StringRes val labelRes: Int
) {
    @SerialName("theme.style.default") @Json(name = "theme.style.default") DEFAULT(R.string.ui_theme_style_default_label),
    @SerialName("theme.style.materialyou") @Json(name = "theme.style.materialyou") MATERIAL_YOU(R.string.ui_theme_style_materialyou_label),
    @SerialName("theme.style.mediumcontrast") @Json(name = "theme.style.mediumcontrast") MEDIUM_CONTRAST(R.string.ui_theme_style_medium_contrast_label),
    @SerialName("theme.style.highcontrast") @Json(name = "theme.style.highcontrast") HIGH_CONTRAST(R.string.ui_theme_style_high_contrast_label),
}
