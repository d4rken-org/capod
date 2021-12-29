package eu.darken.capod.pods.core.airpods.models

import android.bluetooth.le.ScanResult
import eu.darken.capod.pods.core.airpods.ApplePods
import eu.darken.capod.pods.core.airpods.ProximityPairing

data class AirPodsMax constructor(
    override val scanResult: ScanResult,
    override val proximityMessage: ProximityPairing.Message
) : ApplePods