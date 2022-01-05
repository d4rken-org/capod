package eu.darken.capod.pods.core.apple

import android.content.Context
import eu.darken.capod.R
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.lowerNibble
import eu.darken.capod.pods.core.HasSinglePod
import eu.darken.capod.pods.core.getBatteryLevelHeadset

interface BasicSingleApplePods : ApplePods, HasSinglePod {

    override fun getShortStatus(context: Context): String = context.getString(
        R.string.pods_single_basic_status_short,
        getBatteryLevelHeadset(context),
    )

    override val batteryHeadsetPercent: Float?
        get() = when (val value = rawPodsBattery.lowerNibble.toInt()) {
            15 -> null
            else -> if (value > 10) {
                log { "Left pod: Above 100% battery: $value" }
                1.0f
            } else {
                (value / 10f)
            }
        }
}