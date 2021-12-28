package eu.darken.cap.pods.core.airpods

import eu.darken.cap.common.debug.logging.logTag
import eu.darken.cap.pods.core.PodDevice

interface ApplePods : PodDevice {

    val proximityMessage: ProximityPairing.Message

    companion object {

        val TAG = logTag("Pod", "BaseAirPods")
    }
}