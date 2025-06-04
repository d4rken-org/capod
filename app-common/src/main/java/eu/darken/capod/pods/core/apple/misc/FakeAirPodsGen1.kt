package eu.darken.capod.pods.core.apple.misc

import eu.darken.capod.common.bluetooth.BleScanResult
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.pods.core.HasCase
import eu.darken.capod.pods.core.HasChargeDetectionDual
import eu.darken.capod.pods.core.HasEarDetectionDual
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.ApplePods
import eu.darken.capod.pods.core.apple.ApplePodsFactory
import eu.darken.capod.pods.core.apple.DualApplePods
import eu.darken.capod.pods.core.apple.history.PodHistoryRepo
import eu.darken.capod.pods.core.apple.protocol.ProximityMessage
import eu.darken.capod.pods.core.apple.protocol.ProximityPayload
import java.time.Instant
import javax.inject.Inject

/**
 * Basically an AirPods GEN1 clone
 * Similar data structure but a lot of placeholder values or hardcoded values
 * Marketed as TWS i99999
 */
data class FakeAirPodsGen1(
    override val identifier: PodDevice.Id = PodDevice.Id(),
    override val seenLastAt: Instant = Instant.now(),
    override val seenFirstAt: Instant = Instant.now(),
    override val seenCounter: Int = 1,
    override val scanResult: BleScanResult,
    override val payload: ProximityPayload,
    override val reliability: Float = PodDevice.BASE_CONFIDENCE,
    private val rssiAverage: Int? = null,
    private val cachedBatteryPercentage: Float? = null,
) : DualApplePods, HasEarDetectionDual, HasChargeDetectionDual, HasCase {

    override val model: PodDevice.Model = PodDevice.Model.FAKE_AIRPODS_GEN1

    override val batteryCasePercent: Float?
        get() = super.batteryCasePercent ?: cachedBatteryPercentage

    override val rssi: Int
        get() = rssiAverage ?: super<DualApplePods>.rssi

    class Factory @Inject constructor(
        private val repo: PodHistoryRepo,
    ) : ApplePodsFactory {

        override fun isResponsible(message: ProximityMessage): Boolean = message.run {
            // Official message length is 19HEX, i.e. binary 25, did they copy this wrong?
            getModelInfo().full == DEVICE_CODE && length == 19
        }

        override fun create(
            scanResult: BleScanResult,
            payload: ProximityPayload
        ): ApplePods {
            var basic = FakeAirPodsGen1(scanResult = scanResult, payload = payload)
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
            )
        }
    }

    companion object {
        private val DEVICE_CODE = 0x0220.toUShort()
        private val TAG = logTag("PodDevice", "Apple", "Fake", "AirPods", "Gen1")
    }
}