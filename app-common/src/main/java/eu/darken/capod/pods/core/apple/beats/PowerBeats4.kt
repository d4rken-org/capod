package eu.darken.capod.pods.core.apple.beats

import eu.darken.capod.common.bluetooth.BleScanResult
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.ApplePods
import eu.darken.capod.pods.core.apple.ApplePodsFactory
import eu.darken.capod.pods.core.apple.SingleApplePods
import eu.darken.capod.pods.core.apple.airpods.HasStateDetectionAirPods
import eu.darken.capod.pods.core.apple.history.PodHistoryRepo
import eu.darken.capod.pods.core.apple.protocol.ProximityMessage
import eu.darken.capod.pods.core.apple.protocol.ProximityPairing
import eu.darken.capod.pods.core.apple.protocol.ProximityPayload
import java.time.Instant
import javax.inject.Inject

data class PowerBeats4(
    override val identifier: PodDevice.Id = PodDevice.Id(),
    override val seenLastAt: Instant = Instant.now(),
    override val seenFirstAt: Instant = Instant.now(),
    override val seenCounter: Int = 1,
    override val scanResult: BleScanResult,
    override val payload: ProximityPayload,
    override val flags: ApplePods.Flags,
    override val reliability: Float = PodDevice.BASE_CONFIDENCE,
    private val rssiAverage: Int? = null,
) : SingleApplePods, HasStateDetectionAirPods {

    override val model: PodDevice.Model = PodDevice.Model.POWERBEATS_4

    override val rssi: Int
        get() = rssiAverage ?: super<SingleApplePods>.rssi

    class Factory @Inject constructor(
        private val repo: PodHistoryRepo,
    ) : ApplePodsFactory {

        override fun isResponsible(message: ProximityMessage): Boolean = message.run {
            getModelInfo().full == DEVICE_CODE && length == ProximityPairing.PAIRING_MESSAGE_LENGTH
        }

        override fun create(
            scanResult: BleScanResult,
            payload: ProximityPayload,
            flags: ApplePods.Flags
        ): ApplePods {
            var basic = PowerBeats4(scanResult = scanResult, payload = payload, flags = flags)
            val result = repo.search(basic)

            if (result != null) basic = basic.copy(identifier = result.id)
            repo.updateHistory(basic)

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
        private val DEVICE_CODE = 0x0D20.toUShort()
        private val TAG = logTag("PodDevice", "Beats", "PowerBeats", "4")
    }
}