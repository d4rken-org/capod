package eu.darken.capod.pods.core.apple.ble

import android.content.Context
import android.icu.text.RelativeDateTimeFormatter
import androidx.annotation.DrawableRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Battery0Bar
import androidx.compose.material.icons.twotone.Battery1Bar
import androidx.compose.material.icons.twotone.Battery2Bar
import androidx.compose.material.icons.twotone.Battery3Bar
import androidx.compose.material.icons.twotone.Battery4Bar
import androidx.compose.material.icons.twotone.Battery5Bar
import androidx.compose.material.icons.twotone.Battery6Bar
import androidx.compose.material.icons.twotone.BatteryFull
import androidx.compose.material.icons.automirrored.twotone.BatteryUnknown
import androidx.compose.ui.graphics.vector.ImageVector
import eu.darken.capod.R
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

const val BATTERY_UNKNOWN = -1f

fun isKnownBattery(percent: Float): Boolean = percent.isFinite() && percent >= 0f

fun batteryProgress(percent: Float): Float =
    if (isKnownBattery(percent)) percent.coerceIn(0f, 1f) else 0f

fun formatBatteryPercent(context: Context, percent: Float): String =
    if (isKnownBattery(percent)) "${(percent * 100).roundToInt()}%"
    else context.getString(R.string.general_value_not_available_label)

@DrawableRes
fun getBatteryDrawable(percent: Float): Int = when {
    !isKnownBattery(percent) -> R.drawable.ic_baseline_battery_unknown_24
    percent > 0.95f -> R.drawable.ic_baseline_battery_full_24
    percent > 0.80f -> R.drawable.ic_baseline_battery_6_bar_24
    percent > 0.65f -> R.drawable.ic_baseline_battery_5_bar_24
    percent > 0.50f -> R.drawable.ic_baseline_battery_4_bar_24
    percent > 0.35f -> R.drawable.ic_baseline_battery_3_bar_24
    percent > 0.20f -> R.drawable.ic_baseline_battery_2_bar_24
    percent > 0.05f -> R.drawable.ic_baseline_battery_1_bar_24
    else -> R.drawable.ic_baseline_battery_0_bar_24
}

fun getBatteryIcon(percent: Float): ImageVector = when {
    !isKnownBattery(percent) -> Icons.AutoMirrored.TwoTone.BatteryUnknown
    percent > 0.95f -> Icons.TwoTone.BatteryFull
    percent > 0.80f -> Icons.TwoTone.Battery6Bar
    percent > 0.65f -> Icons.TwoTone.Battery5Bar
    percent > 0.50f -> Icons.TwoTone.Battery4Bar
    percent > 0.35f -> Icons.TwoTone.Battery3Bar
    percent > 0.20f -> Icons.TwoTone.Battery2Bar
    percent > 0.05f -> Icons.TwoTone.Battery1Bar
    else -> Icons.TwoTone.Battery0Bar
}

fun BlePodSnapshot.lastSeenFormatted(now: Instant): String {
    val formatter = RelativeDateTimeFormatter.getInstance()
    val duration = Duration.between(seenLastAt, now)
    return if (duration > Duration.ofMinutes(1)) {
        formatter.format(
            duration.toMinutes().toDouble(),
            RelativeDateTimeFormatter.Direction.LAST,
            RelativeDateTimeFormatter.RelativeUnit.MINUTES
        )
    } else {
        formatter.format(
            duration.seconds.toDouble(),
            RelativeDateTimeFormatter.Direction.LAST,
            RelativeDateTimeFormatter.RelativeUnit.SECONDS
        )
    }
}

fun BlePodSnapshot.firstSeenFormatted(now: Instant): String {
    val formatter = RelativeDateTimeFormatter.getInstance()
    val duration = Duration.between(seenFirstAt, now)
    return formatter.format(
        duration.toMinutes().toDouble(),
        RelativeDateTimeFormatter.Direction.LAST,
        RelativeDateTimeFormatter.RelativeUnit.MINUTES
    )
}