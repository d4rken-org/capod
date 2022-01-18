package eu.darken.capod.pods.core.apple.beats

import eu.darken.capod.common.bluetooth.BleScanResult
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.ApplePods
import eu.darken.capod.pods.core.apple.BasicSingleApplePods
import eu.darken.capod.pods.core.apple.protocol.ProximityPairing
import java.time.Instant
import javax.inject.Inject

data class BeatsSolo3(
    override val identifier: PodDevice.Id = PodDevice.Id(),
    override val lastSeenAt: Instant = Instant.now(),
    override val scanResult: BleScanResult,
    override val proximityMessage: ProximityPairing.Message
) : BasicSingleApplePods {

    override val model: PodDevice.Model = PodDevice.Model.BEATS_SOLO_3

    class Factory @Inject constructor() : ApplePods.Factory(TAG) {

        override fun isResponsible(proximityMessage: ProximityPairing.Message): Boolean =
            proximityMessage.getModelInfo().full == DEVICE_CODE

        override fun create(scanResult: BleScanResult, proximityMessage: ProximityPairing.Message): ApplePods {
            val identifier = recognizeDevice(scanResult, proximityMessage)

            return BeatsSolo3(
                identifier = identifier,
                scanResult = scanResult,
                proximityMessage = proximityMessage,
            )
        }

    }

    companion object {
        private val DEVICE_CODE = 0x0620.toUShort()
        private val TAG = logTag("PodDevice", "Beats", "Solo", "3")
    }
}