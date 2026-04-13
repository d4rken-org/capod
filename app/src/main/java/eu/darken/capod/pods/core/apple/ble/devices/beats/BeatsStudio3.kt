package eu.darken.capod.pods.core.apple.ble.devices.beats

import eu.darken.capod.common.SystemTimeSource
import eu.darken.capod.common.bluetooth.BleScanResult
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.pods.core.apple.ble.BlePodSnapshot
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.ble.devices.ApplePods
import eu.darken.capod.pods.core.apple.ble.devices.ApplePodsFactory
import eu.darken.capod.pods.core.apple.ble.devices.SingleApplePods
import eu.darken.capod.pods.core.apple.ble.history.PodHistoryRepo
import eu.darken.capod.pods.core.apple.ble.protocol.ProximityMessage
import eu.darken.capod.pods.core.apple.ble.protocol.ProximityPairing
import eu.darken.capod.pods.core.apple.ble.protocol.ProximityPayload
import java.time.Instant
import javax.inject.Inject

data class BeatsStudio3(
    override val identifier: BlePodSnapshot.Id = BlePodSnapshot.Id(),
    override val seenLastAt: Instant = SystemTimeSource.now(),
    override val seenFirstAt: Instant = SystemTimeSource.now(),
    override val seenCounter: Int = 1,
    override val scanResult: BleScanResult,
    override val payload: ProximityPayload,
    override val meta: ApplePods.AppleMeta,
    override val reliability: Float = BlePodSnapshot.BASE_CONFIDENCE,
    private val rssiAverage: Int? = null,
) : SingleApplePods {

    override val model: PodModel = PodModel.BEATS_STUDIO_3

    class Factory @Inject constructor(
        private val repo: PodHistoryRepo,
    ) : ApplePodsFactory {
        override fun isResponsible(message: ProximityMessage): Boolean = message.run {
            getModelInfo().dirty == DEVICE_CODE_DIRTY && length == ProximityPairing.PAIRING_MESSAGE_LENGTH
        }

        override suspend fun create(
            scanResult: BleScanResult,
            payload: ProximityPayload,
            meta: ApplePods.AppleMeta
        ): ApplePods {
            var basic = BeatsStudio3(scanResult = scanResult, payload = payload, meta = meta)
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
        private val DEVICE_CODE_DIRTY = 9.toUByte()
        private val TAG = logTag("PodDevice", "Beats", "Studio", "3")
    }
}