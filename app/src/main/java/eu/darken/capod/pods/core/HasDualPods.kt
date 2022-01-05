package eu.darken.capod.pods.core

interface HasDualPods {

    enum class Pod {
        LEFT,
        RIGHT
    }

    val batteryLeftPodPercent: Float?

    val batteryRightPodPercent: Float?
}