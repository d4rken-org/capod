package eu.darken.capod.main.core

import androidx.annotation.StringRes
import com.squareup.moshi.Json
import eu.darken.capod.R

enum class ScannerMode(
    val identifier: String,
    @StringRes val labelRes: Int
) {
    @Json(name = "scanner.mode.lowpower") LOW_POWER(
        "scanner.mode.lowpower",
        R.string.settings_scanner_mode_lowpower_label
    ),
    @Json(name = "scanner.mode.balanced") BALANCED(
        "scanner.mode.balanced",
        R.string.settings_scanner_mode_balanced_label
    ),
    @Json(name = "scanner.mode.lowlatency") LOW_LATENCY(
        "scanner.mode.lowlatency",
        R.string.settings_scanner_mode_lowlatency_label
    ),
}