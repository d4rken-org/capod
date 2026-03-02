package eu.darken.capod.main.core

import androidx.annotation.StringRes
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import eu.darken.capod.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@JsonClass(generateAdapter = false)
enum class MonitorMode(
    @StringRes val labelRes: Int
) {
    @SerialName("monitor.mode.manual") @Json(name = "monitor.mode.manual") MANUAL(
        R.string.settings_monitor_mode_manual_label
    ),
    @SerialName("monitor.mode.automatic") @Json(name = "monitor.mode.automatic") AUTOMATIC(
        R.string.settings_monitor_mode_automatic_label
    ),
    @SerialName("monitor.mode.always") @Json(name = "monitor.mode.always") ALWAYS(
        R.string.settings_monitor_mode_always_label
    ),
}