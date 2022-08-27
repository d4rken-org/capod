package eu.darken.capod.pods.core

interface HasChargeDetectionDual : HasChargeDetection {

    val isLeftPodCharging: Boolean

    val isRightPodCharging: Boolean

    val isEitherPodCharging: Boolean
        get() = isLeftPodCharging || isRightPodCharging

    override val isHeadsetBeingCharged: Boolean
        get() = isEitherPodCharging
}