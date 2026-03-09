package eu.darken.capod.common.bluetooth

import androidx.annotation.StringRes
import eu.darken.capod.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ScannerMode(
    val identifier: String,
    @StringRes val labelRes: Int
) {
    @SerialName("scanner.mode.lowpower") LOW_POWER(
        "scanner.mode.lowpower",
        R.string.settings_scanner_mode_lowpower_label
    ),
    @SerialName("scanner.mode.balanced") BALANCED(
        "scanner.mode.balanced",
        R.string.settings_scanner_mode_balanced_label
    ),
    @SerialName("scanner.mode.lowlatency") LOW_LATENCY(
        "scanner.mode.lowlatency",
        R.string.settings_scanner_mode_lowlatency_label
    ),
}