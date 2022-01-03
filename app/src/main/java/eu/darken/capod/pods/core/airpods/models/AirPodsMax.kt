package eu.darken.capod.pods.core.airpods.models

import android.bluetooth.le.ScanResult
import android.content.Context
import eu.darken.capod.pods.core.airpods.ApplePods
import eu.darken.capod.pods.core.airpods.protocol.ProximityPairing
import java.time.Instant
import java.util.*

data class AirPodsMax constructor(
    override val identifier: UUID = UUID.randomUUID(),
    override val lastSeenAt: Instant = Instant.now(),
    override val scanResult: ScanResult,
    override val proximityMessage: ProximityPairing.Message
) : ApplePods {

    override fun getLabel(context: Context): String {
        return "AirPods Max"
    }

}