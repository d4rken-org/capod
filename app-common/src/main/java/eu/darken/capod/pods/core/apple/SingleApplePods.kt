package eu.darken.capod.pods.core.apple

import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.lowerNibble
import eu.darken.capod.pods.core.SinglePodDevice

/**
 * Devices that only present a single charge level, e.g. most Beats devices
 */
interface SingleApplePods : ApplePods, SinglePodDevice, HasAppleColor {

    override val batteryHeadsetPercent: Float?
        get() = when (val value = pubPodsBattery.lowerNibble.toInt()) {
            15 -> null
            else -> if (value > 10) {
                log { "Headset above 100% battery: $value" }
                1.0f
            } else {
                (value / 10f)
            }
        }
}