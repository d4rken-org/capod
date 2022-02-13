package eu.darken.capod.pods.core

interface HasEarDetectionDual : HasEarDetection {

    val isLeftPodInEar: Boolean

    val isRightPodInEar: Boolean

    val isEitherPodInEar: Boolean
        get() = isLeftPodInEar || isRightPodInEar

    override val isBeingWorn: Boolean
        get() = isLeftPodInEar && isRightPodInEar

}