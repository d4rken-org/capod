package eu.darken.capod.pods.core

interface DualPodDevice : PodDevice {

    enum class Pod {
        LEFT,
        RIGHT
    }

    val batteryLeftPodPercent: Float?

    val batteryRightPodPercent: Float?

    val batteryCasePercent: Float?
}