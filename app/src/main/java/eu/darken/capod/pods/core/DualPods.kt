package eu.darken.capod.pods.core

interface DualPods : PodDevice {

    val microPhonePod: Pod

    enum class Pod {
        LEFT,
        RIGHT
    }

    val batteryLeftPodPercent: Float?

    val batteryRightPodPercent: Float?

    val batteryCasePercent: Float?

    val isLeftPodInEar: Boolean

    val isRightPodInEar: Boolean

    val isCaseCharging: Boolean

    val isLeftPodCharging: Boolean

    val isRightPodCharging: Boolean
}