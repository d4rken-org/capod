package eu.darken.capod.pods.core.apple

import android.content.Context
import eu.darken.capod.R
import eu.darken.capod.common.bluetooth.BleScanResult
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.protocol.ProximityPairing
import java.time.Instant
import javax.inject.Inject

data class UnknownAppleDevice(
    override val identifier: PodDevice.Id = PodDevice.Id(),
    override val lastSeenAt: Instant = Instant.now(),
    override val scanResult: BleScanResult,
    override val proximityMessage: ProximityPairing.Message,
    override val rssiHistory: List<Int>
) : ApplePods {

    override val model: PodDevice.Model = PodDevice.Model.UNKNOWN

    override fun getLabel(context: Context): String = context.getString(R.string.pods_unknown_label)

    class Factory @Inject constructor() : ApplePods.Factory(TAG) {
        override fun isResponsible(proximityMessage: ProximityPairing.Message): Boolean = true

        override fun create(
            scanResult: BleScanResult,
            proximityMessage: ProximityPairing.Message,
        ): ApplePods {
            val recognized = recognizeDevice(scanResult, proximityMessage)

            return UnknownAppleDevice(
                identifier = recognized.identifier,
                scanResult = scanResult,
                proximityMessage = proximityMessage,
                rssiHistory = recognized.rssiHistory,
            )
        }
    }

    companion object {
        private val TAG = logTag("PodDevice", "Apple", "Unknown")
    }
}