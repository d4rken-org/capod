package eu.darken.capod.pods.core.apple.airpods

import eu.darken.capod.common.bluetooth.BleScanResult
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.ApplePods
import eu.darken.capod.pods.core.apple.DualApplePods
import eu.darken.capod.pods.core.apple.DualApplePodsFactory
import eu.darken.capod.pods.core.apple.protocol.ProximityPairing
import java.time.Instant
import javax.inject.Inject

data class AirPodsGen2 constructor(
    override val identifier: PodDevice.Id = PodDevice.Id(),
    override val seenLastAt: Instant = Instant.now(),
    override val seenFirstAt: Instant = Instant.now(),
    override val seenCounter: Int = 1,
    override val scanResult: BleScanResult,
    override val proximityMessage: ProximityPairing.Message,
    override val confidence: Float = PodDevice.BASE_CONFIDENCE,
    private val rssiAverage: Int? = null,
    private val cachedBatteryPercentage: Float? = null,
    private val cachedCaseState: DualApplePods.LidState? = null
) : DualApplePods {

    override val model: PodDevice.Model = PodDevice.Model.AIRPODS_GEN2

    override val batteryCasePercent: Float?
        get() = super.batteryCasePercent ?: cachedBatteryPercentage

    override val caseLidState: DualApplePods.LidState
        get() = cachedCaseState ?: super.caseLidState

    override val rssi: Int
        get() = rssiAverage ?: super.rssi

    class Factory @Inject constructor() : DualApplePodsFactory(TAG) {

        override fun isResponsible(proximityMessage: ProximityPairing.Message): Boolean =
            proximityMessage.getModelInfo().full == DEVICE_CODE

        override fun create(scanResult: BleScanResult, proximityMessage: ProximityPairing.Message): ApplePods {
            var basic = AirPodsGen2(scanResult = scanResult, proximityMessage = proximityMessage)
            val result = searchHistory(basic)

            if (result != null) basic = basic.copy(identifier = result.id)
            updateHistory(basic)

            if (result == null) return basic

            return basic.copy(
                identifier = result.id,
                seenFirstAt = result.seenFirstAt,
                seenCounter = result.seenCounter,
                confidence = result.confidence,
                cachedBatteryPercentage = result.getLatestCaseBattery(),
                rssiAverage = result.averageRssi(basic.rssi),
                cachedCaseState = result.getLatestCaseLidState(basic)
            )
        }
    }

    companion object {
        private val DEVICE_CODE = 0x0F20.toUShort()
        private val TAG = logTag("PodDevice", "Apple", "AirPods", "Gen2")
    }
}