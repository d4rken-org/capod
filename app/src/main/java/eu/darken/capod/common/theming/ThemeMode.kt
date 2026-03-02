package eu.darken.capod.common.theming

import androidx.annotation.StringRes
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import eu.darken.capod.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@JsonClass(generateAdapter = false)
enum class ThemeMode(
    @StringRes val labelRes: Int
) {
    @SerialName("theme.mode.system") @Json(name = "theme.mode.system") SYSTEM(R.string.ui_theme_mode_system_label),
    @SerialName("theme.mode.dark") @Json(name = "theme.mode.dark") DARK(R.string.ui_theme_mode_dark_label),
    @SerialName("theme.mode.light") @Json(name = "theme.mode.light") LIGHT(R.string.ui_theme_mode_light_label),
}
