package eu.darken.capod.pods.core.airpods

import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.pods.core.PodDevice

interface ApplePods : PodDevice {

    val proximityMessage: ProximityPairing.Message

    companion object {

        val TAG = logTag("Pod", "BaseAirPods")
    }
}