package eu.darken.capod.pods.core.apple.beats

import eu.darken.capod.common.bluetooth.BleScanResult
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.ApplePods
import eu.darken.capod.pods.core.apple.SingleApplePods
import eu.darken.capod.pods.core.apple.SingleApplePodsFactory
import eu.darken.capod.pods.core.apple.protocol.ProximityPairing
import java.time.Instant
import javax.inject.Inject

data class BeatsStudio3(
    override val identifier: PodDevice.Id = PodDevice.Id(),
    override val seenLastAt: Instant = Instant.now(),
    override val seenFirstAt: Instant = Instant.now(),
    override val seenCounter: Int = 1,
    override val scanResult: BleScanResult,
    override val proximityMessage: ProximityPairing.Message,
    override val reliability: Float = PodDevice.BASE_CONFIDENCE,
    private val rssiAverage: Int? = null,
) : SingleApplePods {

    override val model: PodDevice.Model = PodDevice.Model.BEATS_STUDIO_3

    class Factory @Inject constructor() : SingleApplePodsFactory(TAG) {

        override fun isResponsible(message: ProximityPairing.Message): Boolean = message.run {
            getModelInfo().dirty == DEVICE_CODE_DIRTY && length == ProximityPairing.PAIRING_MESSAGE_LENGTH
        }

        override fun create(scanResult: BleScanResult, message: ProximityPairing.Message): ApplePods {
            var basic = BeatsStudio3(scanResult = scanResult, proximityMessage = message)
            val result = searchHistory(basic)

            if (result != null) basic = basic.copy(identifier = result.id)
            updateHistory(basic)

            if (result == null) return basic

            return basic.copy(
                identifier = result.id,
                seenFirstAt = result.seenFirstAt,
                seenLastAt = scanResult.receivedAt,
                seenCounter = result.seenCounter,
                reliability = result.reliability,
                rssiAverage = result.rssiSmoothed(basic.rssi),
            )
        }
    }

    companion object {
        private val DEVICE_CODE_DIRTY = 9.toUByte()
        private val TAG = logTag("PodDevice", "Beats", "Studio", "3")
    }
}