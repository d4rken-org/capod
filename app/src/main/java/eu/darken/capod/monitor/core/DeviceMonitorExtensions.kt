package eu.darken.capod.monitor.core

import android.content.Context
import android.icu.text.RelativeDateTimeFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

fun DeviceMonitor.devicesWithProfiles(): Flow<List<MonitoredDevice>> = devices
    .map { devices -> devices.filter { it.meta?.profile != null } }

fun DeviceMonitor.primaryDevice(): Flow<MonitoredDevice?> = devicesWithProfiles().map { it.firstOrNull() }

fun MonitoredDevice.getSignalQuality(context: Context): String {
    val multiplier = 100 * signalQuality
    return "${multiplier.roundToInt()}%"
}

fun MonitoredDevice.lastSeenFormatted(now: Instant): String {
    val lastAt = seenLastAt ?: return ""
    val formatter = RelativeDateTimeFormatter.getInstance()
    val duration = Duration.between(lastAt, now)
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

fun MonitoredDevice.firstSeenFormatted(now: Instant): String {
    val firstAt = seenFirstAt ?: return ""
    val formatter = RelativeDateTimeFormatter.getInstance()
    val duration = Duration.between(firstAt, now)
    return formatter.format(
        duration.toMinutes().toDouble(),
        RelativeDateTimeFormatter.Direction.LAST,
        RelativeDateTimeFormatter.RelativeUnit.MINUTES
    )
}
