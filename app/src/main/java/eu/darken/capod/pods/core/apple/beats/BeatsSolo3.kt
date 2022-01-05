package eu.darken.capod.pods.core.apple.beats

import android.bluetooth.le.ScanResult
import android.content.Context
import eu.darken.capod.R
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.BasicSingleApplePods
import eu.darken.capod.pods.core.apple.protocol.ProximityPairing
import java.time.Instant

data class BeatsSolo3 constructor(
    override val identifier: PodDevice.Id = PodDevice.Id(),
    override val lastSeenAt: Instant = Instant.now(),
    override val scanResult: ScanResult,
    override val proximityMessage: ProximityPairing.Message
) : BasicSingleApplePods {

    override fun getLabel(context: Context): String = "Beats Solo 3"

    override val iconRes: Int
        get() = R.drawable.ic_device_generic_earbuds

}