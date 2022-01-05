package eu.darken.capod.pods.core

import android.content.Context
import eu.darken.capod.R
import kotlin.math.roundToInt

fun HasDualPods.getBatteryLevelLeftPod(context: Context): String =
    batteryLeftPodPercent?.let { "${(it * 100).roundToInt()}%" }
        ?: context.getString(R.string.general_value_not_available_label)

fun HasDualPods.getBatteryLevelRightPod(context: Context): String =
    batteryRightPodPercent?.let { "${(it * 100).roundToInt()}%" }
        ?: context.getString(R.string.general_value_not_available_label)

fun HasCase.getBatteryLevelCase(context: Context): String =
    batteryCasePercent?.let { "${(it * 100).roundToInt()}%" }
        ?: context.getString(R.string.general_value_not_available_label)

fun HasSinglePod.getBatteryLevelHeadset(context: Context): String =
    batteryHeadsetPercent?.let { "${(it * 100).roundToInt()}%" }
        ?: context.getString(R.string.general_value_not_available_label)
