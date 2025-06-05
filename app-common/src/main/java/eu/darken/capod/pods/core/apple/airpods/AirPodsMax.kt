package eu.darken.capod.pods.core.apple.airpods

import eu.darken.capod.common.bluetooth.BleScanResult
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.isBitSet
import eu.darken.capod.pods.core.HasChargeDetection
import eu.darken.capod.pods.core.HasEarDetection
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.ApplePods
import eu.darken.capod.pods.core.apple.ApplePodsFactory
import eu.darken.capod.pods.core.apple.HasAppleColor
import eu.darken.capod.pods.core.apple.SingleApplePods
import eu.darken.capod.pods.core.apple.history.PodHistoryRepo
import eu.darken.capod.pods.core.apple.protocol.ProximityMessage
import eu.darken.capod.pods.core.apple.protocol.ProximityPairing
import eu.darken.capod.pods.core.apple.protocol.ProximityPayload
import java.time.Instant
import javax.inject.Inject

data class AirPodsMax(
    override val identifier: PodDevice.Id = PodDevice.Id(),
    override val seenLastAt: Instant = Instant.now(),
    override val seenFirstAt: Instant = Instant.now(),
    override val seenCounter: Int = 1,
    override val scanResult: BleScanResult,
    override val payload: ProximityPayload,
    override val flags: ApplePods.Flags,
    override val reliability: Float = PodDevice.BASE_CONFIDENCE,
    private val rssiAverage: Int? = null,
) : SingleApplePods, HasEarDetection, HasChargeDetection, HasAppleColor {

    override val model: PodDevice.Model = PodDevice.Model.AIRPODS_MAX

    override val rssi: Int
        get() = rssiAverage ?: super<SingleApplePods>.rssi

    override val isHeadsetBeingCharged: Boolean
        get() = pubFlags.isBitSet(0)

    override val isBeingWorn: Boolean
        get() = pubStatus.isBitSet(5)

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
            var basic = AirPodsMax(scanResult = scanResult, payload = payload, flags = flags)
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
        private val DEVICE_CODE = 0x0A20.toUShort()
        private val TAG = logTag("PodDevice", "Apple", "AirPods", "Max")
    }
}