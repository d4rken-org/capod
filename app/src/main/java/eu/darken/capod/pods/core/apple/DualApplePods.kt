package eu.darken.capod.pods.core.apple

import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.isBitSet
import eu.darken.capod.common.lowerNibble
import eu.darken.capod.common.upperNibble
import eu.darken.capod.pods.core.DualPodDevice
import eu.darken.capod.pods.core.DualPodDevice.Pod
import eu.darken.capod.pods.core.HasCase
import eu.darken.capod.pods.core.HasChargeDetectionDual
import eu.darken.capod.pods.core.HasDualMicrophone
import eu.darken.capod.pods.core.HasEarDetectionDual

interface DualApplePods : ApplePods, HasChargeDetectionDual, DualPodDevice, HasEarDetectionDual, HasCase,
    HasDualMicrophone, HasAppleColor {

    val primaryPod: Pod
        get() = when (pubStatus.isBitSet(5)) {
            true -> Pod.LEFT
            false -> Pod.RIGHT
        }

    /**
     * Normally values for the left pod are in the lower nibbles, if the left pod is primary (microphone)
     * If the right pod is the primary, the values are flipped.
     */
    val areValuesFlipped: Boolean
        get() = !pubStatus.isBitSet(5)

    override val batteryLeftPodPercent: Float?
        get() {
            payload.private?.asBatteryState(if (areValuesFlipped) 2 else 1)?.let {
                return it.level
            }

            val value = when (areValuesFlipped) {
                true -> pubPodsBattery.upperNibble.toInt()
                false -> pubPodsBattery.lowerNibble.toInt()
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
            payload.private?.asBatteryState(if (areValuesFlipped) 1 else 2)?.let {
                return it.level
            }

            val value = when (areValuesFlipped) {
                true -> pubPodsBattery.lowerNibble.toInt()
                false -> pubPodsBattery.upperNibble.toInt()
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

    val isThisPodInThecase: Boolean
        get() = pubStatus.isBitSet(6)

    val isOnePodInCase: Boolean
        get() = pubStatus.isBitSet(4)

    val areBothPodsInCase: Boolean
        get() = pubStatus.isBitSet(2)

    override val isLeftPodInEar: Boolean
        get() = when (areValuesFlipped xor isThisPodInThecase) {
            true -> pubStatus.isBitSet(3)
            false -> pubStatus.isBitSet(1)
        }

    override val isRightPodInEar: Boolean
        get() = when (areValuesFlipped xor isThisPodInThecase) {
            true -> pubStatus.isBitSet(1)
            false -> pubStatus.isBitSet(3)
        }

    /**
     * The data flip bit is set if the left pod is primary.
     * For the pod that is in the case, this is flipped again though.
     */
    override val isLeftPodMicrophone: Boolean
        get() = pubStatus.isBitSet(5) xor isThisPodInThecase

    /**
     * The data flip bit is UNset if the right pod is primary.
     * For the pod that is in the case, this is flipped again though.
     */
    override val isRightPodMicrophone: Boolean
        get() = !pubStatus.isBitSet(5) xor isThisPodInThecase

    override val isLeftPodCharging: Boolean
        get() {
            payload.private?.asBatteryState(if (areValuesFlipped) 2 else 1)?.let {
                return it.isCharging
            }
            return when (areValuesFlipped) {
                false -> pubFlags.isBitSet(0)
                true -> pubFlags.isBitSet(1)
            }
        }

    override val isRightPodCharging: Boolean
        get() {
            payload.private?.asBatteryState(if (areValuesFlipped) 1 else 2)?.let {
                return it.isCharging
            }
            return when (areValuesFlipped) {
                false -> pubFlags.isBitSet(1)
                true -> pubFlags.isBitSet(0)
            }
        }

    override val batteryCasePercent: Float?
        get() {
            payload.private?.asBatteryState(3)?.let { return it.level }

            return when (val value = pubCaseBattery.toInt()) {
                15 -> null
                else -> if (value > 10) {
                    log { "Case: Above 100% battery: $value" }
                    1.0f
                } else {
                    value / 10f
                }
            }
        }

    override val isCaseCharging: Boolean
        get() {
            payload.private?.asBatteryState(3)?.let { return it.isCharging }

            return pubFlags.isBitSet(2)
        }

    val caseLidState: LidState
        get() {
            val rawstate = pubCaseLidState
            return LidState.entries.firstOrNull { it.rawRange.contains(rawstate.toInt()) } ?: LidState.UNKNOWN
        }

    /**
     * TODO this is glitchy
     * The counters generally increase if quickly and repeatedly:
     * - open/close
     * - add/remove the last airpod to the case
     * They reset after some time to their start values.
     * The upper limits are not the maximums but are only reached if playing with the case.
     */
    enum class LidState(val rawRange: IntRange) {
        OPEN(0x30..0x37),
        CLOSED(0x38..0x3F),
        NOT_IN_CASE(0x00..0x03),
        UNKNOWN(0xFF..0xFF);
    }

}