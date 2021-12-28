package eu.darken.cap.pods.core.airpods.models

import android.bluetooth.le.ScanResult
import eu.darken.cap.pods.core.airpods.ApplePods
import eu.darken.cap.pods.core.airpods.ProximityPairing

data class UnknownAppleDevice constructor(
    override val scanResult: ScanResult,
    override val proximityMessage: ProximityPairing.Message
) : ApplePods