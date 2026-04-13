package eu.darken.capod.pods.core.apple.ble.devices.misc

import androidx.annotation.DrawableRes
import eu.darken.capod.R
import eu.darken.capod.common.SystemTimeSource
import eu.darken.capod.common.bluetooth.BleScanResult
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.pods.core.apple.ble.BlePodSnapshot
import eu.darken.capod.pods.core.apple.ble.devices.HasCase
import eu.darken.capod.pods.core.apple.ble.devices.HasChargeDetectionDual
import eu.darken.capod.pods.core.apple.ble.devices.HasEarDetectionDual
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.ble.devices.ApplePods
import eu.darken.capod.pods.core.apple.ble.devices.ApplePodsFactory
import eu.darken.capod.pods.core.apple.ble.devices.DualApplePods
import eu.darken.capod.pods.core.apple.ble.history.PodHistoryRepo
import eu.darken.capod.pods.core.apple.ble.protocol.ProximityMessage
import eu.darken.capod.pods.core.apple.ble.protocol.ProximityPayload
import java.time.Instant
import javax.inject.Inject

/**
 * AirPods Gen3 clone
 * Shorter data structure.
 * https://discord.com/channels/548521543039189022/927235844127993866/1054404630118924308
 */
data class FakeAirPodsGen3(
    override val identifier: BlePodSnapshot.Id = BlePodSnapshot.Id(),
    override val seenLastAt: Instant = SystemTimeSource.now(),
    override val seenFirstAt: Instant = SystemTimeSource.now(),
    override val seenCounter: Int = 1,
    override val scanResult: BleScanResult,
    override val payload: ProximityPayload,
    override val meta: ApplePods.AppleMeta,
    override val reliability: Float = BlePodSnapshot.BASE_CONFIDENCE,
    private val rssiAverage: Int? = null,
    private val cachedBatteryPercentage: Float? = null,
) : DualApplePods, HasEarDetectionDual, HasChargeDetectionDual, HasCase {

    override val model: PodModel = PodModel.FAKE_AIRPODS_GEN3

    @get:DrawableRes
    override val leftPodIcon: Int
        get() = R.drawable.device_airpods_gen3_left

    @get:DrawableRes
    override val rightPodIcon: Int
        get() = R.drawable.device_airpods_gen3_right

    @get:DrawableRes
    override val caseIcon: Int
        get() = R.drawable.device_airpods_gen3_case

    override val rssi: Int
        get() = rssiAverage ?: super.rssi

    override val batteryCasePercent: Float?
        get() = super.batteryCasePercent ?: cachedBatteryPercentage

    class Factory @Inject constructor(
        private val repo: PodHistoryRepo,
    ) : ApplePodsFactory {
        override fun isResponsible(message: ProximityMessage): Boolean = message.run {
            // Official message length is 19HEX, i.e. binary 25, did they copy this wrong?
            getModelInfo().full == DEVICE_CODE && length == 19
        }

        override suspend fun create(
            scanResult: BleScanResult,
            payload: ProximityPayload,
            meta: ApplePods.AppleMeta
        ): ApplePods {
            var basic = FakeAirPodsGen3(scanResult = scanResult, payload = payload, meta = meta)
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
        private val DEVICE_CODE = 0x1320.toUShort()
        private val TAG = logTag("PodDevice", "Apple", "Fake", "AirPods", "Gen3")
    }
}