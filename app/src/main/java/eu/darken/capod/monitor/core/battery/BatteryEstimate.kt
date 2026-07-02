package eu.darken.capod.monitor.core.battery

/**
 * Per-pod time-remaining estimates for one device. Each earbud is measured and shown independently
 * (the mic pod, for example, drains faster). [headset] is used for single-headset devices; [left] /
 * [right] for dual-pod devices. Slots with no usable estimate are null.
 */
data class BatteryEstimate(
    val left: Pod? = null,
    val right: Pod? = null,
    val headset: Pod? = null,
) {
    /**
     * @property minutesRemaining smoothed estimate of minutes until this pod empties
     * @property fractionPerHour the drain rate it was derived from (fraction/hour)
     * @property source how the drain was determined (see [Source]) — reflects measurement provenance,
     *   NOT whether the model rating capped the shown value; a LIVE/LEARNED estimate can still be
     *   bounded by the model's rated life
     * @property minutesUntilCharged minutes until this pod is full — non-null only while it is
     *   actively charging with a usable charge rate (not during an Optimized Battery Charging hold
     *   or the final trickle phase). Shown inside the charging chip; [minutesRemaining] stays on
     *   the gauge line as the runtime projection.
     */
    data class Pod(
        val minutesRemaining: Int,
        val fractionPerHour: Float,
        val source: Source,
        val minutesUntilCharged: Int? = null,
    ) {
        /** True while the estimate rests on the model's rated spec, before any drain has been measured. */
        val isProvisional: Boolean get() = source == Source.SPEC
    }

    /** Where a pod's drain rate was derived from, in order of increasing confidence. */
    enum class Source {
        /** Apple's published rating — shown immediately on connect, before any drain is observed. */
        SPEC,

        /** Persisted rate from earlier sessions with this device. */
        LEARNED,

        /** Measured from the current session's observed drain. */
        LIVE,
    }

    val hasAny: Boolean get() = left != null || right != null || headset != null
}
