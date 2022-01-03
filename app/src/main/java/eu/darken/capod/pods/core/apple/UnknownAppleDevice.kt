package eu.darken.capod.pods.core.apple

import android.bluetooth.le.ScanResult
import android.content.Context
import eu.darken.capod.R
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.pods.core.apple.protocol.ProximityPairing
import java.time.Instant
import java.util.*

data class UnknownAppleDevice constructor(
    override val identifier: UUID = UUID.randomUUID(),
    override val lastSeenAt: Instant = Instant.now(),
    override val scanResult: ScanResult,
    override val proximityMessage: ProximityPairing.Message
) : ApplePods {

    override fun getLabel(context: Context): String {
        return context.getString(R.string.device_unknown_label)
    }

    override val tag: String = logTag("Pod", "Apple", "Unknown")
}