package eu.darken.capod.pods.core.apple.aap.protocol

/**
 * Device-reported settings. Pure domain — no wire protocol bytes.
 * Each subclass represents a capability with its current state and supported values.
 * The [AapDeviceProfile] handles all wire ↔ domain translation.
 */
sealed class AapSetting {

    data class AncMode(
        val current: Value,
        val supported: List<Value>,
    ) : AapSetting() {
        enum class Value {
            OFF, ON, TRANSPARENCY, ADAPTIVE,
        }
    }

    data class ConversationalAwareness(
        val enabled: Boolean,
    ) : AapSetting()

    data class PressSpeed(
        val value: Value,
    ) : AapSetting() {
        enum class Value(val wireValue: Int) {
            DEFAULT(0x00), SLOWER(0x01), SLOWEST(0x02);

            companion object {
                fun fromWire(value: Int): Value? = entries.firstOrNull { it.wireValue == value }
            }
        }
    }

    data class PressHoldDuration(
        val value: Value,
    ) : AapSetting() {
        enum class Value(val wireValue: Int) {
            DEFAULT(0x00), SHORTER(0x01), SHORTEST(0x02);

            companion object {
                fun fromWire(value: Int): Value? = entries.firstOrNull { it.wireValue == value }
            }
        }
    }

    data class NcWithOneAirPod(
        val enabled: Boolean,
    ) : AapSetting()

    data class ToneVolume(
        val level: Int,
    ) : AapSetting()

    data class VolumeSwipeLength(
        val value: Value,
    ) : AapSetting() {
        enum class Value(val wireValue: Int) {
            DEFAULT(0x00), LONGER(0x01), LONGEST(0x02);

            companion object {
                fun fromWire(value: Int): Value? = entries.firstOrNull { it.wireValue == value }
            }
        }
    }

    data class EndCallMuteMic(
        val muteMic: MuteMicMode,
        val endCall: EndCallMode,
    ) : AapSetting() {
        enum class MuteMicMode(val wireValue: Int) {
            SINGLE_PRESS(0x23), DOUBLE_PRESS(0x22);

            companion object {
                fun fromWire(value: Int): MuteMicMode? = entries.firstOrNull { it.wireValue == value }
            }
        }

        enum class EndCallMode(val wireValue: Int) {
            DOUBLE_PRESS(0x02), SINGLE_PRESS(0x03);

            companion object {
                fun fromWire(value: Int): EndCallMode? = entries.firstOrNull { it.wireValue == value }
            }
        }
    }

    data class VolumeSwipe(
        val enabled: Boolean,
    ) : AapSetting()

    data class PersonalizedVolume(
        val enabled: Boolean,
    ) : AapSetting()

    data class AdaptiveAudioNoise(
        val level: Int,
    ) : AapSetting()

    /** Push-only from device — reports speaking detection state (command 0x4B). */
    data class ConversationalAwarenessState(
        val speaking: Boolean,
    ) : AapSetting()
}
