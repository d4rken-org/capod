package eu.darken.capod.pods.core

import androidx.annotation.DrawableRes
import eu.darken.capod.R

interface HasCase {

    val batteryCasePercent: Float?

    val isCaseCharging: Boolean

    @get:DrawableRes
    val caseIcon: Int
        get() = R.drawable.device_airpods_gen1_case
}