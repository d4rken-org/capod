package eu.darken.capod.pods.core.apple.airpods

import androidx.annotation.DrawableRes
import eu.darken.capod.common.R
import eu.darken.capod.common.bluetooth.BleScanResult
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.ApplePods
import eu.darken.capod.pods.core.apple.DualAirPods
import eu.darken.capod.pods.core.apple.DualAirPods.LidState
import eu.darken.capod.pods.core.apple.DualApplePodsFactory
import eu.darken.capod.pods.core.apple.protocol.ProximityPairing
import java.time.Instant
import javax.inject.Inject

data class AirPodsPro2(
    override val identifier: PodDevice.Id = PodDevice.Id(),
    override val seenLastAt: Instant = Instant.now(),
    override val seenFirstAt: Instant = Instant.now(),
    override val seenCounter: Int = 1,
    override val scanResult: BleScanResult,
    override val proximityMessage: ProximityPairing.Message,
    override val confidence: Float = PodDevice.BASE_CONFIDENCE,
    private val rssiAverage: Int? = null,
    private val cachedBatteryPercentage: Float? = null,
    private val cachedCaseState: LidState? = null
) : DualAirPods {

    override val model: PodDevice.Model = PodDevice.Model.AIRPODS_PRO2

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

    override val batteryCasePercent: Float?
        get() = super.batteryCasePercent ?: cachedBatteryPercentage

    override val caseLidState: LidState
        get() = cachedCaseState ?: super.caseLidState

    override val rssi: Int
        get() = rssiAverage ?: super.rssi

    class Factory @Inject constructor() : DualApplePodsFactory(TAG) {

        override fun isResponsible(message: ProximityPairing.Message): Boolean = message.run {
            getModelInfo().full == DEVICE_CODE && length == ProximityPairing.PAIRING_MESSAGE_LENGTH
        }

        override fun create(scanResult: BleScanResult, message: ProximityPairing.Message): ApplePods {
            var basic = AirPodsPro2(scanResult = scanResult, proximityMessage = message)
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
        private val DEVICE_CODE = 0x1420.toUShort()
        private val TAG = logTag("PodDevice", "Apple", "AirPods", "Pro2")
    }
}