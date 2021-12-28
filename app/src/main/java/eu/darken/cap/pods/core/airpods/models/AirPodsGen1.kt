package eu.darken.cap.pods.core.airpods.models

import android.bluetooth.le.ScanResult
import eu.darken.cap.common.debug.logging.logTag
import eu.darken.cap.pods.core.airpods.AirPodsDevice
import eu.darken.cap.pods.core.airpods.ProximityPairing

data class AirPodsGen1 constructor(
    override val scanResult: ScanResult,
    override val proximityMessage: ProximityPairing.Message
) : AirPodsDevice {
    override val tag: String = logTag("Pod", "AirPodsGen1")
}