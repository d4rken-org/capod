package eu.darken.capod.pods.core.apple.airpods

import eu.darken.capod.common.bluetooth.BleScanResult
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.isBitSet
import eu.darken.capod.pods.core.HasEarDetection
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.ApplePods
import eu.darken.capod.pods.core.apple.SingleApplePods
import eu.darken.capod.pods.core.apple.SingleApplePodsFactory
import eu.darken.capod.pods.core.apple.protocol.ProximityPairing
import java.time.Instant
import javax.inject.Inject

data class AirPodsMax(
    override val identifier: PodDevice.Id = PodDevice.Id(),
    override val seenLastAt: Instant = Instant.now(),
    override val seenFirstAt: Instant = Instant.now(),
    override val seenCounter: Int = 1,
    override val scanResult: BleScanResult,
    override val proximityMessage: ProximityPairing.Message,
    override val confidence: Float = PodDevice.BASE_CONFIDENCE,
    private val rssiAverage: Int? = null,
) : SingleApplePods, HasEarDetection {

    override val model: PodDevice.Model = PodDevice.Model.AIRPODS_MAX

    override val rssi: Int
        get() = rssiAverage ?: super.rssi

    val isHeadphonesBeingWorn: Boolean
        get() = rawStatus.isBitSet(1)

    val isHeadsetBeingCharged: Boolean
        get() = rawFlags.isBitSet(0)

    override val isBeingWorn: Boolean
        get() = isHeadphonesBeingWorn

    class Factory @Inject constructor() : SingleApplePodsFactory(TAG) {

        override fun isResponsible(message: ProximityPairing.Message): Boolean = message.run {
            getModelInfo().dirty == DEVICE_CODE_DIRTY && length == ProximityPairing.PAIRING_MESSAGE_LENGTH
        }

        override fun create(scanResult: BleScanResult, message: ProximityPairing.Message): ApplePods {
            var basic = AirPodsMax(scanResult = scanResult, proximityMessage = message)
            val result = searchHistory(basic)

            if (result != null) basic = basic.copy(identifier = result.id)
            updateHistory(basic)

            if (result == null) return basic

            return basic.copy(
                identifier = result.id,
                seenFirstAt = result.seenFirstAt,
                seenLastAt = scanResult.receivedAt,
                seenCounter = result.seenCounter,
                confidence = result.confidence,
                rssiAverage = result.averageRssi(basic.rssi),
            )
        }
    }

    companion object {
        private val DEVICE_CODE_DIRTY = 10.toUByte()
        private val TAG = logTag("PodDevice", "Apple", "AirPods", "Max")
    }
}