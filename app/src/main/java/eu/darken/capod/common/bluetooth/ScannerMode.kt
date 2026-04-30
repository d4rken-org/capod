package eu.darken.capod.common.bluetooth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ScannerMode {
    @SerialName("scanner.mode.lowpower") LOW_POWER,
    @SerialName("scanner.mode.balanced") BALANCED,
    @SerialName("scanner.mode.lowlatency") LOW_LATENCY,
}
