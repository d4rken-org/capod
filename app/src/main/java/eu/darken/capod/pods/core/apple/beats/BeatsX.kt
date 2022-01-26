package eu.darken.capod.pods.core.apple.beats

import eu.darken.capod.common.bluetooth.BleScanResult
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.ApplePods
import eu.darken.capod.pods.core.apple.BasicSingleApplePods
import eu.darken.capod.pods.core.apple.protocol.ProximityPairing
import java.time.Instant
import javax.inject.Inject

data class BeatsX(
    override val identifier: PodDevice.Id = PodDevice.Id(),
    override val lastSeenAt: Instant = Instant.now(),
    override val scanResult: BleScanResult,
    override val proximityMessage: ProximityPairing.Message,
    override val rssiHistory: List<Int>,
) : BasicSingleApplePods {

    override val model: PodDevice.Model = PodDevice.Model.BEATS_X

    class Factory @Inject constructor() : ApplePods.Factory(TAG) {

        override fun isResponsible(proximityMessage: ProximityPairing.Message): Boolean =
            proximityMessage.getModelInfo().full == DEVICE_CODE

        override fun create(scanResult: BleScanResult, proximityMessage: ProximityPairing.Message): ApplePods {
            val recognized = recognizeDevice(scanResult, proximityMessage)

            return BeatsX(
                identifier = recognized.identifier,
                scanResult = scanResult,
                proximityMessage = proximityMessage,
                rssiHistory = recognized.rssiHistory,
            )
        }

    }

    companion object {
        private val DEVICE_CODE = 0x0520.toUShort()
        private val TAG = logTag("PodDevice", "Beats", "X")
    }
}