package eu.darken.capod.common.bluetooth

import androidx.annotation.StringRes
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import eu.darken.capod.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@JsonClass(generateAdapter = false)
enum class ScannerMode(
    val identifier: String,
    @StringRes val labelRes: Int
) {
    @SerialName("scanner.mode.lowpower") @Json(name = "scanner.mode.lowpower") LOW_POWER(
        "scanner.mode.lowpower",
        R.string.settings_scanner_mode_lowpower_label
    ),
    @SerialName("scanner.mode.balanced") @Json(name = "scanner.mode.balanced") BALANCED(
        "scanner.mode.balanced",
        R.string.settings_scanner_mode_balanced_label
    ),
    @SerialName("scanner.mode.lowlatency") @Json(name = "scanner.mode.lowlatency") LOW_LATENCY(
        "scanner.mode.lowlatency",
        R.string.settings_scanner_mode_lowlatency_label
    ),
}