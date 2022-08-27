package eu.darken.capod.pods.core.apple

import android.content.Context
import androidx.annotation.StringRes
import eu.darken.capod.R
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.isBitSet
import eu.darken.capod.common.lowerNibble
import eu.darken.capod.common.upperNibble
import eu.darken.capod.pods.core.*
import eu.darken.capod.pods.core.DualPodDevice.Pod

interface DualAirPods : ApplePods, HasChargeDetectionDual, DualPodDevice, HasEarDetectionDual, HasCase,
    HasStateDetection, HasDualMicrophone, HasAppleColor {

    val primaryPod: Pod
        get() = when (rawStatus.isBitSet(5)) {
            true -> Pod.LEFT
            false -> Pod.RIGHT
        }

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

    val isThisPodInThecase: Boolean
        get() = rawStatus.isBitSet(6)

    val isOnePodInCase: Boolean
        get() = rawStatus.isBitSet(4)

    val areBothPodsInCase: Boolean
        get() = rawStatus.isBitSet(2)

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

    /**
     * The data flip bit is set if the left pod is primary.
     * For the pod that is in the case, this is flipped again though.
     */
    override val isLeftPodMicrophone: Boolean
        get() = rawStatus.isBitSet(5) xor isThisPodInThecase

    /**
     * The data flip bit is UNset if the right pod is primary.
     * For the pod that is in the case, this is flipped again though.
     */
    override val isRightPodMicrophone: Boolean
        get() = !rawStatus.isBitSet(5) xor isThisPodInThecase

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

    override val batteryCasePercent: Float?
        get() = when (val value = rawCaseBattery.toInt()) {
            15 -> null
            else -> if (value > 10) {
                log { "Case: Above 100% battery: $value" }
                1.0f
            } else {
                value / 10f
            }
        }

    override val isCaseCharging: Boolean
        get() = rawFlags.isBitSet(2)

    val caseLidState: LidState
        get() {
            val rawstate = rawCaseLidState
            return LidState.values().firstOrNull { it.rawRange.contains(rawstate.toInt()) } ?: LidState.UNKNOWN
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

    override val state: ConnectionState
        get() = ConnectionState.values().firstOrNull { rawSuffix == it.raw } ?: ConnectionState.UNKNOWN

    enum class ConnectionState(val raw: UByte?, @StringRes val labelRes: Int) : HasStateDetection.State {
        DISCONNECTED(0x00, R.string.pods_connection_state_disconnected_label),
        IDLE(0x04, R.string.pods_connection_state_idle_label),
        MUSIC(0x05, R.string.pods_connection_state_music_label),
        CALL(0x06, R.string.pods_connection_state_call_label),
        RINGING(0x07, R.string.pods_connection_state_ringing_label),
        HANGING_UP(0x09, R.string.pods_connection_state_hanging_up_label),
        UNKNOWN(null, R.string.pods_connection_state_unknown_label);

        override fun getLabel(context: Context): String = context.getString(labelRes)

        constructor(raw: Int, @StringRes labelRes: Int) : this(raw.toUByte(), labelRes)
    }

}