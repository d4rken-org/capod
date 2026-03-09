package eu.darken.capod.main.core

import androidx.annotation.StringRes
import eu.darken.capod.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MonitorMode(
    @StringRes val labelRes: Int
) {
    @SerialName("monitor.mode.manual") MANUAL(
        R.string.settings_monitor_mode_manual_label
    ),
    @SerialName("monitor.mode.automatic") AUTOMATIC(
        R.string.settings_monitor_mode_automatic_label
    ),
    @SerialName("monitor.mode.always") ALWAYS(
        R.string.settings_monitor_mode_always_label
    ),
}