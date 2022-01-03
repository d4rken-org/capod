package eu.darken.capod.pods.core.apple.airpods

import android.bluetooth.le.ScanResult
import android.content.Context
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.pods.core.apple.DualApplePods
import eu.darken.capod.pods.core.apple.protocol.ProximityPairing
import java.time.Instant
import java.util.*

data class AirPodsPro constructor(
    override val identifier: UUID = UUID.randomUUID(),
    override val lastSeenAt: Instant = Instant.now(),
    override val scanResult: ScanResult,
    override val proximityMessage: ProximityPairing.Message
) : DualApplePods {

    override fun getLabel(context: Context): String {
        return "AirPods Pro"
    }

    override val tag: String = logTag("Pod", "Apple", "AirPods", "Pro")
}