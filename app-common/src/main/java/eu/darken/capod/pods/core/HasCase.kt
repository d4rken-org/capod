package eu.darken.capod.pods.core

import androidx.annotation.DrawableRes
import eu.darken.capod.common.R

interface HasCase {

    val batteryCasePercent: Float?

    val isCaseCharging: Boolean

    @get:DrawableRes
    val caseIcon: Int
        get() = R.drawable.devic_airpods_gen1_case
}