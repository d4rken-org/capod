package eu.darken.capod.pods.core.apple.beats

import eu.darken.capod.common.bluetooth.BleScanResult
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.ApplePods
import eu.darken.capod.pods.core.apple.DualAirPods
import eu.darken.capod.pods.core.apple.DualApplePodsFactory
import eu.darken.capod.pods.core.apple.protocol.ProximityPairing
import java.time.Instant
import javax.inject.Inject

data class PowerBeatsPro(
    override val identifier: PodDevice.Id = PodDevice.Id(),
    override val seenLastAt: Instant = Instant.now(),
    override val seenFirstAt: Instant = Instant.now(),
    override val seenCounter: Int = 1,
    override val scanResult: BleScanResult,
    override val proximityMessage: ProximityPairing.Message,
    override val confidence: Float = PodDevice.BASE_CONFIDENCE,
    private val rssiAverage: Int? = null,
    private val cachedBatteryPercentage: Float? = null,
    private val cachedCaseState: DualAirPods.LidState? = null
) : DualAirPods {

    override val model: PodDevice.Model = PodDevice.Model.POWERBEATS_PRO

    override val batteryCasePercent: Float?
        get() = super.batteryCasePercent ?: cachedBatteryPercentage

    override val caseLidState: DualAirPods.LidState
        get() = cachedCaseState ?: super.caseLidState

    override val rssi: Int
        get() = rssiAverage ?: super.rssi

    class Factory @Inject constructor() : DualApplePodsFactory(TAG) {

        override fun isResponsible(message: ProximityPairing.Message): Boolean = message.run {
            getModelInfo().dirty == DEVICE_CODE_DIRTY && length == ProximityPairing.PAIRING_MESSAGE_LENGTH
        }

        override fun create(scanResult: BleScanResult, message: ProximityPairing.Message): ApplePods {
            var basic = PowerBeatsPro(scanResult = scanResult, proximityMessage = message)
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
                cachedBatteryPercentage = result.getLatestCaseBattery(),
                rssiAverage = result.averageRssi(basic.rssi),
                cachedCaseState = result.getLatestCaseLidState(basic)
            )
        }

    }

    companion object {
        private val DEVICE_CODE_DIRTY = 11.toUByte()
        private val TAG = logTag("PodDevice", "Beats", "PowerBeats", "Pro")
    }
}