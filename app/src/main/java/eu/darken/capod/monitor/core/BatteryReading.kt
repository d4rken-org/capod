package eu.darken.capod.monitor.core

import eu.darken.capod.pods.core.apple.ble.BATTERY_RESOLUTION_DECILE
import eu.darken.capod.pods.core.apple.ble.BATTERY_RESOLUTION_PERCENT
import eu.darken.capod.pods.core.apple.ble.devices.HasCase

/**
 * A battery percentage together with the step size of the source it came from. A reading of 20%
 * from a decile source means "somewhere in [20%, 30%)", which is the difference between a claim we
 * can make and one we can't.
 */
data class BatteryReading(
    val percent: Float,
    val resolution: Float,
)

/**
 * [PodDevice.batteryCase] resolved through the same precedence, but keeping the resolution of
 * whichever source won. Cache entries store no resolution, so they report the coarsest one.
 */
val PodDevice.batteryCaseReading: BatteryReading?
    get() {
        aap?.batteryCase?.let { return BatteryReading(it, BATTERY_RESOLUTION_PERCENT) }
        (ble as? HasCase)?.let { snapshot ->
            snapshot.batteryCasePercent?.let { return BatteryReading(it, snapshot.batteryCaseResolution) }
        }
        cached?.case?.percent?.let { return BatteryReading(it, BATTERY_RESOLUTION_DECILE) }
        return null
    }
