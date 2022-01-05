package eu.darken.capod.pods.core.apple

import android.bluetooth.le.ScanResult
import android.content.Context
import eu.darken.capod.R
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.protocol.ProximityPairing
import java.time.Instant

data class UnknownAppleDevice constructor(
    override val identifier: PodDevice.Id = PodDevice.Id(),
    override val lastSeenAt: Instant = Instant.now(),
    override val scanResult: ScanResult,
    override val proximityMessage: ProximityPairing.Message
) : ApplePods {

    override fun getLabel(context: Context): String {
        return context.getString(R.string.device_unknown_label)
    }

    override fun getShortStatus(context: Context): String {
        return context.getString(R.string.device_unknown_label)
    }
}