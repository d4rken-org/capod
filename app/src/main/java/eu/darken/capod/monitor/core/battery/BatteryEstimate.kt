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
     * @property isLearned true when the rate came from persisted history rather than the live session
     */
    data class Pod(
        val minutesRemaining: Int,
        val fractionPerHour: Float,
        val isLearned: Boolean,
    )

    val hasAny: Boolean get() = left != null || right != null || headset != null
}
