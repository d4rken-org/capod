package eu.darken.capod.pods.core.apple.ble.devices

import androidx.annotation.DrawableRes
import eu.darken.capod.R
import eu.darken.capod.pods.core.apple.ble.BATTERY_RESOLUTION_DECILE

interface HasCase {

    val batteryCasePercent: Float?

    /**
     * Step size of [batteryCasePercent]. Deciles unless a decoder knows it read a finer source, so
     * a caller reasoning about how much the true value may exceed the reading errs on the coarse
     * side.
     */
    val batteryCaseResolution: Float
        get() = BATTERY_RESOLUTION_DECILE

    val isCaseCharging: Boolean

    @get:DrawableRes
    val caseIcon: Int
        get() = R.drawable.device_airpods_gen1_case
}
