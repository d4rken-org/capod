package eu.darken.capod.main.core

import androidx.annotation.StringRes
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import eu.darken.capod.R

@JsonClass(generateAdapter = false)
enum class MonitorMode(
    @StringRes val labelRes: Int
) {
    @Json(name = "monitor.mode.manual") MANUAL(
        R.string.settings_monitor_mode_manual_label
    ),
    @Json(name = "monitor.mode.automatic") AUTOMATIC(
        R.string.settings_monitor_mode_automatic_label
    ),
    @Json(name = "monitor.mode.always") ALWAYS(
        R.string.settings_monitor_mode_always_label
    ),
    @Json(name = "monitor.mode.periodically") PERIODICALLY(
        R.string.settings_monitor_mode_periodically_label
    ),
}