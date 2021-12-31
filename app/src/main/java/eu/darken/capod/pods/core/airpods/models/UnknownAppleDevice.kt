package eu.darken.capod.pods.core.airpods.models

import android.bluetooth.le.ScanResult
import android.content.Context
import eu.darken.capod.pods.core.airpods.ApplePods
import eu.darken.capod.pods.core.airpods.protocol.ProximityPairing

data class UnknownAppleDevice constructor(
    override val scanResult: ScanResult,
    override val proximityMessage: ProximityPairing.Message
) : ApplePods {

    override fun getLabel(context: Context): String {
        return "Unknown device"
    }

}