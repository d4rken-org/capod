package eu.darken.capod.pods.core.apple.misc

import androidx.annotation.DrawableRes
import eu.darken.capod.common.R
import eu.darken.capod.common.bluetooth.BleScanResult
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.isBitSet
import eu.darken.capod.common.lowerNibble
import eu.darken.capod.common.upperNibble
import eu.darken.capod.pods.core.*
import eu.darken.capod.pods.core.apple.ApplePods
import eu.darken.capod.pods.core.apple.ApplePodsFactory
import eu.darken.capod.pods.core.apple.protocol.ProximityPairing
import java.time.Instant
import javax.inject.Inject

/**
 * AirPods Pro clone similar to Twsi999999.
 * Shorter data structure.
 * https://discord.com/channels/548521543039189022/927235844127993866/962068944196358234
 */
data class FakeAirPodsPro constructor(
    override val identifier: PodDevice.Id = PodDevice.Id(),
    override val seenLastAt: Instant = Instant.now(),
    override val seenFirstAt: Instant = Instant.now(),
    override val seenCounter: Int = 1,
    override val scanResult: BleScanResult,
    override val proximityMessage: ProximityPairing.Message,
    override val confidence: Float = PodDevice.BASE_CONFIDENCE,
    private val rssiAverage: Int? = null,
    private val cachedBatteryPercentage: Float? = null,
) : ApplePods, DualPodDevice, HasChargeDetectionDual, HasEarDetectionDual, HasCase {

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
        get() = rssiAverage ?: super<ApplePods>.rssi

    /**
     * Normally values for the left pod are in the lower nibbles, if the left pod is primary (microphone)
     * If the right pod is the primary, the values are flipped.
     */
    val areValuesFlipped: Boolean
        get() = !rawStatus.isBitSet(5)

    override val batteryLeftPodPercent: Float?
        get() {
            val value = when (areValuesFlipped) {
                true -> rawPodsBattery.upperNibble.toInt()
                false -> rawPodsBattery.lowerNibble.toInt()
            }
            return when (value) {
                15 -> null
                else -> if (value > 10) {
                    log { "Left pod: Above 100% battery: $value" }
                    1.0f
                } else {
                    (value / 10f)
                }
            }
        }

    override val batteryRightPodPercent: Float?
        get() {
            val value = when (areValuesFlipped) {
                true -> rawPodsBattery.lowerNibble.toInt()
                false -> rawPodsBattery.upperNibble.toInt()
            }
            return when (value) {
                15 -> null
                else -> if (value > 10) {
                    log { "Right pod: Above 100% battery: $value" }
                    1.0f
                } else {
                    value / 10f
                }
            }
        }

    override val isLeftPodCharging: Boolean
        get() = when (areValuesFlipped) {
            false -> rawFlags.isBitSet(0)
            true -> rawFlags.isBitSet(1)
        }

    override val isRightPodCharging: Boolean
        get() = when (areValuesFlipped) {
            false -> rawFlags.isBitSet(1)
            true -> rawFlags.isBitSet(0)
        }

    private val isThisPodInThecase: Boolean
        get() = rawStatus.isBitSet(6)

    override val isLeftPodInEar: Boolean
        get() = when (areValuesFlipped xor isThisPodInThecase) {
            true -> rawStatus.isBitSet(3)
            false -> rawStatus.isBitSet(1)
        }

    override val isRightPodInEar: Boolean
        get() = when (areValuesFlipped xor isThisPodInThecase) {
            true -> rawStatus.isBitSet(1)
            false -> rawStatus.isBitSet(3)
        }

    override val batteryCasePercent: Float?
        get() = when (val value = rawCaseBattery.toInt()) {
            15 -> cachedBatteryPercentage
            else -> if (value > 10) {
                log { "Case: Above 100% battery: $value" }
                1.0f
            } else {
                value / 10f
            }
        }

    override val isCaseCharging: Boolean
        get() = rawFlags.isBitSet(2)

    class Factory @Inject constructor() : ApplePodsFactory<FakeAirPodsPro>(TAG) {

        override fun isResponsible(message: ProximityPairing.Message): Boolean = message.run {
            // Official message length is 19HEX, i.e. binary 25, did they copy this wrong?
            getModelInfo().full == DEVICE_CODE && length == 19
        }

        override fun create(scanResult: BleScanResult, message: ProximityPairing.Message): ApplePods {
            var basic = FakeAirPodsPro(scanResult = scanResult, proximityMessage = message)
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
            )
        }
    }

    companion object {
        private val DEVICE_CODE = 0x0E20.toUShort()
        private val TAG = logTag("PodDevice", "Apple", "Fake", "AirPods", "Pro")
    }
}