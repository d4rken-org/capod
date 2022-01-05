package eu.darken.capod.pods.core.apple.airpods

import android.bluetooth.le.ScanResult
import android.content.Context
import eu.darken.capod.R
import eu.darken.capod.pods.core.apple.SingleApplePods
import eu.darken.capod.pods.core.apple.protocol.ProximityPairing
import java.time.Instant
import java.util.*

data class AirPodsMax constructor(
    override val identifier: UUID = UUID.randomUUID(),
    override val lastSeenAt: Instant = Instant.now(),
    override val scanResult: ScanResult,
    override val proximityMessage: ProximityPairing.Message,
) : SingleApplePods {

    override fun getLabel(context: Context): String = "AirPods Max"

    override val iconRes: Int
        get() = R.drawable.ic_device_generic_headphones

}