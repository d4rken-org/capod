package eu.darken.capod.monitor.core

import android.content.Context
import android.icu.text.RelativeDateTimeFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

fun DeviceMonitor.devicesWithProfiles(): Flow<List<PodDevice>> = devices
    .map { devices -> devices.filter { it.profileId != null } }

fun DeviceMonitor.primaryDevice(): Flow<PodDevice?> = devicesWithProfiles().map { it.firstOrNull() }

fun PodDevice.getSignalQuality(context: Context): String {
    val multiplier = 100 * signalQuality
    return "${multiplier.roundToInt()}%"
}

fun PodDevice.lastSeenFormatted(now: Instant): String {
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

fun PodDevice.firstSeenFormatted(now: Instant): String {
    val firstAt = seenFirstAt ?: return ""
    val formatter = RelativeDateTimeFormatter.getInstance()
    val duration = Duration.between(firstAt, now)
    return formatter.format(
        duration.toMinutes().toDouble(),
        RelativeDateTimeFormatter.Direction.LAST,
        RelativeDateTimeFormatter.RelativeUnit.MINUTES
    )
}

fun PodDevice.cachedBatteryFormatted(now: Instant): String {
    val cachedAt = cachedBatteryAt ?: return ""
    val formatter = RelativeDateTimeFormatter.getInstance()
    val duration = Duration.between(cachedAt, now)
    return when {
        duration > Duration.ofHours(1) -> formatter.format(
            duration.toHours().toDouble(),
            RelativeDateTimeFormatter.Direction.LAST,
            RelativeDateTimeFormatter.RelativeUnit.HOURS
        )
        duration > Duration.ofMinutes(1) -> formatter.format(
            duration.toMinutes().toDouble(),
            RelativeDateTimeFormatter.Direction.LAST,
            RelativeDateTimeFormatter.RelativeUnit.MINUTES
        )
        else -> formatter.format(
            duration.seconds.toDouble(),
            RelativeDateTimeFormatter.Direction.LAST,
            RelativeDateTimeFormatter.RelativeUnit.SECONDS
        )
    }
}

