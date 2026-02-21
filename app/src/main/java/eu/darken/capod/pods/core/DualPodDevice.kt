package eu.darken.capod.pods.core

import androidx.annotation.DrawableRes
import eu.darken.capod.R

interface DualPodDevice : PodDevice {

    enum class Pod {
        LEFT,
        RIGHT
    }

    val batteryLeftPodPercent: Float?

    val batteryRightPodPercent: Float?

    @get:DrawableRes
    val leftPodIcon: Int
        get() = R.drawable.device_airpods_gen1_left

    @get:DrawableRes
    val rightPodIcon: Int
        get() = R.drawable.device_airpods_gen1_right
}