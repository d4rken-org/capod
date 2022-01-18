package eu.darken.capod.pods.core.apple.airpods

import eu.darken.capod.common.bluetooth.BleScanResult
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.ApplePods
import eu.darken.capod.pods.core.apple.SingleApplePods
import eu.darken.capod.pods.core.apple.protocol.ProximityPairing
import java.time.Instant
import javax.inject.Inject

data class AirPodsMax(
    override val identifier: PodDevice.Id = PodDevice.Id(),
    override val lastSeenAt: Instant = Instant.now(),
    override val scanResult: BleScanResult,
    override val proximityMessage: ProximityPairing.Message,
) : SingleApplePods {

    override val model: PodDevice.Model = PodDevice.Model.AIRPODS_MAX

    class Factory @Inject constructor() : ApplePods.Factory(TAG) {

        override fun isResponsible(proximityMessage: ProximityPairing.Message): Boolean =
            proximityMessage.getModelInfo().dirty == DEVICE_CODE_DIRTY

        override fun create(scanResult: BleScanResult, proximityMessage: ProximityPairing.Message): ApplePods {
            val identifier = recognizeDevice(scanResult, proximityMessage)

            return AirPodsMax(
                identifier = identifier,
                scanResult = scanResult,
                proximityMessage = proximityMessage,
            )
        }

    }

    companion object {
        private val DEVICE_CODE_DIRTY = 10.toUByte()
        private val TAG = logTag("PodDevice", "Apple", "AirPods", "Max")
    }
}