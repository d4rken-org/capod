package eu.darken.capod.pods.core.airpods.models

import android.bluetooth.le.ScanResult
import android.content.Context
import eu.darken.capod.R
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.pods.core.airpods.DualApplePods
import eu.darken.capod.pods.core.airpods.protocol.ProximityPairing
import java.time.Instant
import java.util.*

data class AirPodsGen1 constructor(
    override val identifier: UUID = UUID.randomUUID(),
    override val lastSeenAt: Instant = Instant.now(),
    override val scanResult: ScanResult,
    override val proximityMessage: ProximityPairing.Message
) : DualApplePods {

    override fun getLabel(context: Context): String {
        return "AirPods (Gen 1)"
    }

    override val iconRes: Int
        get() = R.drawable.ic_device_airpods_gen1

    override val tag: String = logTag("Pod", "AirPodsGen1")
}