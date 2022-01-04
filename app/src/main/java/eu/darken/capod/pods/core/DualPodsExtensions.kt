package eu.darken.capod.pods.core

import android.content.Context
import eu.darken.capod.R
import kotlin.math.roundToInt

fun DualPodDevice.getBatteryLevelLeftPod(context: Context): String =
    batteryLeftPodPercent?.let { "${(it * 100).roundToInt()}%" }
        ?: context.getString(R.string.general_value_not_available_label)

fun DualPodDevice.getBatteryLevelRightPod(context: Context): String =
    batteryRightPodPercent?.let { "${(it * 100).roundToInt()}%" }
        ?: context.getString(R.string.general_value_not_available_label)

fun DualPodDevice.getBatteryLevelCase(context: Context): String =
    batteryCasePercent?.let { "${(it * 100).roundToInt()}%" }
        ?: context.getString(R.string.general_value_not_available_label)
