package eu.darken.capod.pods.core.apple.ble.devices.airpods

import eu.darken.capod.common.SystemTimeSource
import eu.darken.capod.common.bluetooth.BleScanResult
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.isBitSet
import eu.darken.capod.pods.core.apple.ble.BlePodSnapshot
import eu.darken.capod.pods.core.apple.ble.devices.HasChargeDetection
import eu.darken.capod.pods.core.apple.ble.devices.HasEarDetection
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.ble.devices.ApplePods
import eu.darken.capod.pods.core.apple.ble.devices.ApplePodsFactory
import eu.darken.capod.pods.core.apple.ble.devices.HasAppleColor
import eu.darken.capod.pods.core.apple.ble.devices.SingleApplePods
import eu.darken.capod.pods.core.apple.ble.history.PodHistoryRepo
import eu.darken.capod.pods.core.apple.ble.protocol.ProximityMessage
import eu.darken.capod.pods.core.apple.ble.protocol.ProximityPairing
import eu.darken.capod.pods.core.apple.ble.protocol.ProximityPayload
import java.time.Instant
import javax.inject.Inject

data class AirPodsMax2(
    override val identifier: BlePodSnapshot.Id = BlePodSnapshot.Id(),
    override val seenLastAt: Instant = SystemTimeSource.now(),
    override val seenFirstAt: Instant = SystemTimeSource.now(),
    override val seenCounter: Int = 1,
    override val scanResult: BleScanResult,
    override val payload: ProximityPayload,
    override val meta: ApplePods.AppleMeta,
    override val reliability: Float = BlePodSnapshot.BASE_CONFIDENCE,
    private val rssiAverage: Int? = null,
) : SingleApplePods, HasEarDetection, HasChargeDetection, HasAppleColor {

    override val model: PodModel = PodModel.AIRPODS_MAX2

    override val rssi: Int
        get() = rssiAverage ?: super<SingleApplePods>.rssi

    override val isHeadsetBeingCharged: Boolean
        get() {
            payload.private?.asBatteryState(1)?.let {
                return it.isCharging
            }
            return pubFlags.isBitSet(0)
        }

    // Aggregate "any wear sensor active" — NOT "both earcups worn".
    // Observed on A3454: bit 5 of pubStatus is always set while advertising and
    // no longer carries the wear flag (unlike Max gen 1). Bits 1 and 3 of
    // pubStatus reflect the two earcup sensors (same byte positions used by
    // DualApplePods, but without the primary/flip semantics).
    //
    // We OR them because some Android pairings only see one of the two bits
    // reliably (issue #548: "both worn" advertises as 0x23 — bit 1 only —
    // while macOS sees 0x2B with both bits set). AND-ing would falsely report
    // "not worn" during normal use on those phones.
    override val isBeingWorn: Boolean
        get() = pubStatus.isBitSet(1) || pubStatus.isBitSet(3)

    class Factory @Inject constructor(
        private val repo: PodHistoryRepo,
    ) : ApplePodsFactory {
        override fun isResponsible(message: ProximityMessage): Boolean = message.run {
            getModelInfo().full == DEVICE_CODE && length == ProximityPairing.PAIRING_MESSAGE_LENGTH
        }

        override suspend fun create(
            scanResult: BleScanResult,
            payload: ProximityPayload,
            meta: ApplePods.AppleMeta
        ): ApplePods {
            var basic = AirPodsMax2(scanResult = scanResult, payload = payload, meta = meta)
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
        private val DEVICE_CODE = 0x2D20.toUShort()
        private val TAG = logTag("PodDevice", "Apple", "AirPods", "Max2")
    }
}
