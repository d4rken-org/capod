package eu.darken.capod.pods.core.apple.beats

import android.bluetooth.le.ScanResult
import android.content.Context
import eu.darken.capod.R
import eu.darken.capod.pods.core.apple.BasicSingleApplePods
import eu.darken.capod.pods.core.apple.protocol.ProximityPairing
import java.time.Instant
import java.util.*

data class BeatsFlex constructor(
    override val identifier: UUID = UUID.randomUUID(),
    override val lastSeenAt: Instant = Instant.now(),
    override val scanResult: ScanResult,
    override val proximityMessage: ProximityPairing.Message
) : BasicSingleApplePods {

    override fun getLabel(context: Context): String = "Beats Flex"

    override val iconRes: Int
        get() = R.drawable.ic_device_generic_earbuds

}