package eu.darken.capod.pods.core

import android.content.Context
import android.icu.text.RelativeDateTimeFormatter
import eu.darken.capod.R
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

fun HasDualPods.getBatteryLevelLeftPod(context: Context): String =
    batteryLeftPodPercent?.let { "${(it * 100).roundToInt()}%" }
        ?: context.getString(R.string.general_value_not_available_label)

fun HasDualPods.getBatteryLevelRightPod(context: Context): String =
    batteryRightPodPercent?.let { "${(it * 100).roundToInt()}%" }
        ?: context.getString(R.string.general_value_not_available_label)

fun HasCase.getBatteryLevelCase(context: Context): String =
    batteryCasePercent?.let { "${(it * 100).roundToInt()}%" }
        ?: context.getString(R.string.general_value_not_available_label)

fun HasSinglePod.getBatteryLevelHeadset(context: Context): String =
    batteryHeadsetPercent?.let { "${(it * 100).roundToInt()}%" }
        ?: context.getString(R.string.general_value_not_available_label)

private val lastSeenFormatter = RelativeDateTimeFormatter.getInstance()

fun PodDevice.lastSeenFormatted(now: Instant): String {
    val duration = Duration.between(lastSeenAt, now)
    return lastSeenFormatter.format(
        duration.seconds.toDouble(),
        RelativeDateTimeFormatter.Direction.LAST,
        RelativeDateTimeFormatter.RelativeUnit.SECONDS
    )
}