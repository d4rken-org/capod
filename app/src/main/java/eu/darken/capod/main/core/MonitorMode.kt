package eu.darken.capod.main.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MonitorMode(
    val intensity: Int,
) {
    @SerialName("monitor.mode.manual") MANUAL(intensity = 0),
    @SerialName("monitor.mode.automatic") AUTOMATIC(intensity = 1),
    @SerialName("monitor.mode.always") ALWAYS(intensity = 2),
}
