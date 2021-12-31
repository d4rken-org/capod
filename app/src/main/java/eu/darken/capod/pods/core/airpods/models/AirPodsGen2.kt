package eu.darken.capod.pods.core.airpods.models

import android.bluetooth.le.ScanResult
import android.content.Context
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.pods.core.airpods.DualApplePods
import eu.darken.capod.pods.core.airpods.protocol.ProximityPairing

data class AirPodsGen2 constructor(
    override val scanResult: ScanResult,
    override val proximityMessage: ProximityPairing.Message
) : DualApplePods {

    override fun getLabel(context: Context): String {
        return "AirPods (Gen 2)"
    }

    override val tag: String = logTag("Pod", "AirPodsGen2")
}