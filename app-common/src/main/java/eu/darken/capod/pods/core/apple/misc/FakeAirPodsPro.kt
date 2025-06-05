package eu.darken.capod.pods.core.apple.misc

import androidx.annotation.DrawableRes
import eu.darken.capod.common.R
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
 * AirPods Pro clone similar to Twsi999999.
 * Shorter data structure.
 * https://discord.com/channels/548521543039189022/927235844127993866/962068944196358234
 */
data class FakeAirPodsPro(
    override val identifier: PodDevice.Id = PodDevice.Id(),
    override val seenLastAt: Instant = Instant.now(),
    override val seenFirstAt: Instant = Instant.now(),
    override val seenCounter: Int = 1,
    override val scanResult: BleScanResult,
    override val payload: ProximityPayload,
    override val flags: ApplePods.Flags,
    override val reliability: Float = PodDevice.BASE_CONFIDENCE,
    private val rssiAverage: Int? = null,
    private val cachedBatteryPercentage: Float? = null,
) : DualApplePods, HasChargeDetectionDual, HasEarDetectionDual, HasCase {

    override val model: PodDevice.Model = PodDevice.Model.FAKE_AIRPODS_PRO

    @get:DrawableRes
    override val iconRes: Int
        get() = R.drawable.devic_airpods_pro2_both

    @get:DrawableRes
    override val caseIcon: Int
        get() = R.drawable.devic_airpods_pro2_case

    @get:DrawableRes
    override val leftPodIcon: Int
        get() = R.drawable.devic_airpods_pro2_left

    @get:DrawableRes
    override val rightPodIcon: Int
        get() = R.drawable.devic_airpods_pro2_right

    override val rssi: Int
        get() = rssiAverage ?: super<DualApplePods>.rssi

    override val batteryCasePercent: Float?
        get() = super.batteryCasePercent ?: cachedBatteryPercentage

    class Factory @Inject constructor(
        private val repo: PodHistoryRepo,
    ) : ApplePodsFactory {

        override fun isResponsible(message: ProximityMessage): Boolean = message.run {
            // Official message length is 19HEX, i.e. binary 25, did they copy this wrong?
            getModelInfo().full == DEVICE_CODE && length == 19
        }

        override fun create(
            scanResult: BleScanResult,
            payload: ProximityPayload,
            flags: ApplePods.Flags
        ): ApplePods {
            var basic = FakeAirPodsPro(scanResult = scanResult, payload = payload, flags = flags)
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
        private val DEVICE_CODE = 0x0E20.toUShort()
        private val TAG = logTag("PodDevice", "Apple", "Fake", "AirPods", "Pro")
    }
}