package eu.darken.capod.pods.core.apple

import eu.darken.capod.common.isBitSet
import eu.darken.capod.pods.core.HasEarDetection
import eu.darken.capod.pods.core.HasSinglePod

interface SingleApplePods : BasicSingleApplePods, HasEarDetection, HasSinglePod {

    val isHeadphonesBeingWorn: Boolean
        get() = rawStatus.isBitSet(1)

    val isHeadsetBeingCharged: Boolean
        get() = rawFlags.isBitSet(0)

    override val isBeingWorn: Boolean
        get() = isHeadphonesBeingWorn
}