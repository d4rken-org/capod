package eu.darken.capod.main.core

import androidx.annotation.StringRes
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import eu.darken.capod.R

@JsonClass(generateAdapter = false)
enum class MonitorMode(
    val identifier: String,
    @StringRes val labelRes: Int
) {
    @Json(name = "monitor.mode.manual") MANUAL(
        "monitor.mode.manual",
        R.string.settings_monitor_mode_manual_label
    ),
    @Json(name = "monitor.mode.automatic") AUTOMATIC(
        "monitor.mode.automatic",
        R.string.settings_monitor_mode_automatic_label
    ),
    @Json(name = "monitor.mode.always") ALWAYS(
        "monitor.mode.always",
        R.string.settings_monitor_mode_always_label
    ),
}