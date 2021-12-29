package eu.darken.capod.pods.core.airpods.models

import android.bluetooth.le.ScanResult
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.pods.core.airpods.AirPodsDevice
import eu.darken.capod.pods.core.airpods.ProximityPairing

data class AirPodsPro constructor(
    override val scanResult: ScanResult,
    override val proximityMessage: ProximityPairing.Message
) : AirPodsDevice {

    override val tag: String = logTag("Pod", "AirPodsPro")
}