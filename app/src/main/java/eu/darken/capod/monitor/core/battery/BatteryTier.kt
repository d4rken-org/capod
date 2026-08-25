package eu.darken.capod.monitor.core.battery

/** Battery bands behind the overview's colour semantics. */
enum class BatteryTier {
    UNKNOWN,
    CRITICAL,
    WARN,
    GOOD,
}

/**
 * Maps a battery fraction onto a [BatteryTier]. Only a finite, non-negative fraction has a level;
 * anything else is [BatteryTier.UNKNOWN]. Values above 1 count as full.
 */
fun batteryTier(percent: Float): BatteryTier {
    if (!percent.isFinite() || percent < 0f) return BatteryTier.UNKNOWN
    val clamped = percent.coerceIn(0f, 1f)
    return when {
        clamped > 0.30f -> BatteryTier.GOOD
        clamped >= 0.15f -> BatteryTier.WARN
        else -> BatteryTier.CRITICAL
    }
}
