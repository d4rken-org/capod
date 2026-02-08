package eu.darken.capod.pods.core

import android.content.Context
import android.icu.text.RelativeDateTimeFormatter
import androidx.annotation.DrawableRes
import eu.darken.capod.R
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

fun formatBatteryPercent(context: Context, percent: Float?): String =
    percent?.let { "${(it * 100).roundToInt()}%" }
        ?: context.getString(R.string.general_value_not_available_label)

fun PodDevice.getSignalQuality(context: Context): String {
    val multiplier = 100 * signalQuality
    return "${multiplier.roundToInt()}%"
}

@DrawableRes
fun getBatteryDrawable(percent: Float?): Int = when {
    percent == null -> R.drawable.ic_baseline_battery_unknown_24
    percent > 0.95f -> R.drawable.ic_baseline_battery_full_24
    percent > 0.80f -> R.drawable.ic_baseline_battery_6_bar_24
    percent > 0.65f -> R.drawable.ic_baseline_battery_5_bar_24
    percent > 0.50f -> R.drawable.ic_baseline_battery_4_bar_24
    percent > 0.35f -> R.drawable.ic_baseline_battery_3_bar_24
    percent > 0.20f -> R.drawable.ic_baseline_battery_2_bar_24
    percent > 0.05f -> R.drawable.ic_baseline_battery_1_bar_24
    else -> R.drawable.ic_baseline_battery_0_bar_24
}

fun PodDevice.lastSeenFormatted(now: Instant): String {
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

fun PodDevice.firstSeenFormatted(now: Instant): String {
    val formatter = RelativeDateTimeFormatter.getInstance()
    val duration = Duration.between(seenFirstAt, now)
    return formatter.format(
        duration.toMinutes().toDouble(),
        RelativeDateTimeFormatter.Direction.LAST,
        RelativeDateTimeFormatter.RelativeUnit.MINUTES
    )
}