package eu.darken.capod.pods.core

import android.content.Context
import eu.darken.capod.R
import kotlin.math.roundToInt

fun SinglePodDevice.getBatteryLevelHeadset(context: Context): String =
    batteryHeadsetPercent?.let { "${(it * 100).roundToInt()}%" }
        ?: context.getString(R.string.general_value_not_available_label)
