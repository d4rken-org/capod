package eu.darken.capod.pods.core.apple

import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.lowerNibble
import eu.darken.capod.pods.core.HasSinglePod

interface BasicSingleApplePods : ApplePods, HasSinglePod {

    override val batteryHeadsetPercent: Float?
        get() = when (val value = rawPodsBattery.lowerNibble.toInt()) {
            15 -> null
            else -> if (value > 10) {
                log(tag) { "Left pod: Above 100% battery: $value" }
                1.0f
            } else {
                (value / 10f)
            }
        }
}