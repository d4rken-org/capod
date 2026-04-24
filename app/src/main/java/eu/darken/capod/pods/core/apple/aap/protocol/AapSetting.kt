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

    // UI-space 0..100 (100 = max noise reduction). Wire value is inverted — conversion lives in
    // the device profile. Pro 3 silently accepts writes (no echo) but the value persists.
    data class AdaptiveAudioNoise(
        val level: Int,
    ) : AapSetting()

    /** Push-only from device — reports speaking detection state (command 0x4B). */
    data class ConversationalAwarenessState(
        val speaking: Boolean,
    ) : AapSetting()

    data class MicrophoneMode(
        val mode: Mode,
    ) : AapSetting() {
        enum class Mode(val wireValue: Int) {
            AUTO(0x00), ALWAYS_RIGHT(0x01), ALWAYS_LEFT(0x02);

            companion object {
                fun fromWire(value: Int): Mode? = entries.firstOrNull { it.wireValue == value }
            }
        }
    }

    data class EarDetectionEnabled(
        val enabled: Boolean,
    ) : AapSetting()

    data class ListeningModeCycle(
        val modeMask: Int,
    ) : AapSetting() {
        val includesOff: Boolean get() = (modeMask and 0x01) != 0
        val includesAnc: Boolean get() = (modeMask and 0x02) != 0
        val includesTransparency: Boolean get() = (modeMask and 0x04) != 0
        val includesAdaptive: Boolean get() = (modeMask and 0x08) != 0
    }

    data class AllowOffOption(
        val enabled: Boolean,
    ) : AapSetting()

    data class StemConfig(
        val claimedPressMask: Int,
    ) : AapSetting() {
        val claimsSinglePress: Boolean get() = (claimedPressMask and 0x01) != 0
        val claimsDoublePress: Boolean get() = (claimedPressMask and 0x02) != 0
        val claimsTriplePress: Boolean get() = (claimedPressMask and 0x04) != 0
        val claimsLongPress: Boolean get() = (claimedPressMask and 0x08) != 0
    }

    data class SleepDetection(
        val enabled: Boolean,
    ) : AapSetting()

    data class InCaseTone(
        val enabled: Boolean,
    ) : AapSetting()

    data class ConnectedDevices(
        val devices: List<ConnectedDevice>,
    ) : AapSetting() {
        data class ConnectedDevice(val mac: String, val type: Int)
    }

    data class AudioSource(
        val sourceMac: String?,
        val type: AudioSourceType,
    ) : AapSetting() {
        enum class AudioSourceType { NONE, CALL, MEDIA }
    }

    /**
     * Payload of message type 0x0053 — "PME Config" in the Wireshark AAP dissector.
     * PME = Personal Medical Equipment (cf. PPE = Personal Protective Equipment):
     * the hearing-aid configuration for Apple's iOS 18.1+ hearing-aid feature on
     * AirPods Pro 2.
     *
     * Decoded as 4 × 8 Float32 values — consistent with per-ear × per-profile
     * audiogram band gains (e.g. L/R × two environment profiles, 8 frequency
     * bands). CAPod previously called this "EQ bands".
     *
     * Callers should treat all-zero [sets] as "no hearing-aid profile configured"
     * — stock firmware reports zeros until the user runs Apple's Hearing Test /
     * hearing-aid setup.
     */
    data class PmeConfig(
        val sets: List<List<Float>>,
    ) : AapSetting() {
        val isAllZero: Boolean
            get() = sets.all { set -> set.all { it == 0f } }
    }

    /** Per-pod placement reported by the device (command 0x06). */
    data class EarDetection(
        val primaryPod: PodPlacement,
        val secondaryPod: PodPlacement,
    ) : AapSetting() {
        enum class PodPlacement {
            IN_EAR, NOT_IN_EAR, IN_CASE, DISCONNECTED,
        }

        val isEitherPodInEar: Boolean
            get() = primaryPod == PodPlacement.IN_EAR || secondaryPod == PodPlacement.IN_EAR
    }

    /** Which physical pod currently holds the microphone (command 0x08). */
    data class PrimaryPod(
        val pod: Pod,
    ) : AapSetting() {
        enum class Pod { LEFT, RIGHT }
    }

    /**
     * Catch-all for setting IDs whose semantics are unconfirmed.
     * Decoded to keep lastMessageAt fresh and log via the normal "Setting:" path.
     * Not exposed in UI. When a setting's purpose is confirmed, promote it to
     * its own named subclass.
     */
    data class UnknownSetting(
        val settingId: Int,
        val rawValue: Int,
    ) : AapSetting() {
        override fun toString(): String = "UnknownSetting(settingId=0x%02X, rawValue=0x%02X)".format(settingId, rawValue)
    }
}
