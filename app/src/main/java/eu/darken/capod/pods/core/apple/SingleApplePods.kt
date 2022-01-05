package eu.darken.capod.pods.core.apple

import android.content.Context
import eu.darken.capod.R
import eu.darken.capod.common.isBitSet
import eu.darken.capod.common.upperNibble
import eu.darken.capod.pods.core.HasEarDetection
import eu.darken.capod.pods.core.HasSinglePod
import eu.darken.capod.pods.core.getBatteryLevelHeadset

interface SingleApplePods : BasicSingleApplePods, HasEarDetection, HasSinglePod {

    override fun getShortStatus(context: Context): String = context.getString(
        R.string.pods_single_basic_status_short,
        getBatteryLevelHeadset(context),
    )

    val isHeadphonesBeingWorn: Boolean
        get() = rawStatus.isBitSet(1)

    val isHeadsetBeingCharged: Boolean
        get() = rawCaseBattery.upperNibble.isBitSet(0)

    override val isBeingWorn: Boolean
        get() = isHeadphonesBeingWorn
}