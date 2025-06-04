package eu.darken.capod.pods.core.apple.airpods

import eu.darken.capod.common.bluetooth.BleScanResult
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.ApplePods
import eu.darken.capod.pods.core.apple.ApplePodsFactory
import eu.darken.capod.pods.core.apple.DualApplePods
import eu.darken.capod.pods.core.apple.history.PodHistoryRepo
import eu.darken.capod.pods.core.apple.protocol.ProximityMessage
import eu.darken.capod.pods.core.apple.protocol.ProximityPairing
import eu.darken.capod.pods.core.apple.protocol.ProximityPayload
import java.time.Instant
import javax.inject.Inject

data class AirPodsGen1(
    override val identifier: PodDevice.Id = PodDevice.Id(),
    override val seenLastAt: Instant = Instant.now(),
    override val seenFirstAt: Instant = Instant.now(),
    override val seenCounter: Int = 1,
    override val scanResult: BleScanResult,
    override val payload: ProximityPayload,
    override val reliability: Float = PodDevice.BASE_CONFIDENCE,
    private val rssiAverage: Int? = null,
    private val cachedBatteryPercentage: Float? = null,
    private val cachedCaseState: DualApplePods.LidState? = null
) : DualApplePods {

    override val model: PodDevice.Model = PodDevice.Model.AIRPODS_GEN1

    override val batteryCasePercent: Float?
        get() = super.batteryCasePercent ?: cachedBatteryPercentage

    override val caseLidState: DualApplePods.LidState
        get() = cachedCaseState ?: super.caseLidState

    override val rssi: Int
        get() = rssiAverage ?: super.rssi

    class Factory @Inject constructor(
        private val repo: PodHistoryRepo,
    ) : ApplePodsFactory {

        override fun isResponsible(message: ProximityMessage): Boolean = message.run {
            getModelInfo().full == DEVICE_CODE && length == ProximityPairing.PAIRING_MESSAGE_LENGTH
        }

        override fun create(
            scanResult: BleScanResult,
            payload: ProximityPayload
        ): ApplePods {
            var basic = AirPodsGen1(scanResult = scanResult, payload = payload)
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
                cachedBatteryPercentage = result.getLatestCaseBattery(),
                rssiAverage = result.rssiSmoothed(basic.rssi),
                cachedCaseState = result.getLatestCaseLidState(basic)
            )
        }
    }

    companion object {
        private val DEVICE_CODE = 0x0220.toUShort()
        private val TAG = logTag("PodDevice", "Apple", "AirPods", "Gen1")
    }
}