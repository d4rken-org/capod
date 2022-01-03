package eu.darken.capod.pods.core.apple

import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.isBitSet
import eu.darken.capod.common.lowerNibble
import eu.darken.capod.common.upperNibble

interface SingleApplePods : BasicSingleApplePods {

    val batteryCasePercent: Float?
        get() = when (val value = rawCaseBattery.lowerNibble.toInt()) {
            15 -> null
            else -> if (value > 10) {
                log(tag) { "Case: Above 100% battery: $value" }
                1.0f
            } else {
                value / 10f
            }
        }

    val isHeadsetBeingWorn: Boolean
        get() = rawStatus.isBitSet(1)

    val isCaseCharging: Boolean
        get() = rawCaseBattery.upperNibble.isBitSet(2)

    val isHeadsetBeingCharged: Boolean
        get() = rawCaseBattery.upperNibble.isBitSet(0)
}