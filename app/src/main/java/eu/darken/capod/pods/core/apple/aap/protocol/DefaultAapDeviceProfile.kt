package eu.darken.capod.pods.core.apple.aap.protocol

import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.AapPodState
import kotlin.reflect.KClass

/**
 * Default AAP device profile covering the known protocol from MagicPodsCore + LibrePods research.
 * Handles the common wire format used by all Apple/Beats devices over L2CAP PSM 0x1001.
 *
 * Model-specific behavior (supported ANC modes, InitExt, feature gating) is driven by
 * [PodModel.features] — no subclassing needed.
 */
class DefaultAapDeviceProfile(
    private val model: PodModel = PodModel.UNKNOWN,
) : AapDeviceProfile {

    companion object {
        // AAP command types (bytes 4-5 of the message, little-endian)
        const val CMD_SETTINGS = 0x0009
        const val CMD_BATTERY = 0x0004
        const val CMD_DEVICE_INFO = 0x001D
        const val CMD_PRIVATE_KEYS_RESPONSE = 0x0031
        const val CMD_EAR_DETECTION = 0x0006
        const val CMD_CONVERSATION_AWARENESS_STATE = 0x004B

        // Setting IDs (first byte of settings command payload)
        const val SETTING_ANC_MODE = 0x0D
        const val SETTING_PRESS_SPEED = 0x17
        const val SETTING_PRESS_HOLD_DURATION = 0x18
        const val SETTING_NC_ONE_AIRPOD = 0x1B
        const val SETTING_TONE_VOLUME = 0x1F
        const val SETTING_VOLUME_SWIPE_LENGTH = 0x23
        const val SETTING_END_CALL_MUTE_MIC = 0x24
        const val SETTING_VOLUME_SWIPE = 0x25
        const val SETTING_PERSONALIZED_VOLUME = 0x26
        const val SETTING_CONVERSATIONAL_AWARENESS = 0x28
        const val SETTING_ADAPTIVE_AUDIO_NOISE = 0x2E

        // ANC mode wire values
        const val ANC_WIRE_OFF = 0x01
        const val ANC_WIRE_ON = 0x02
        const val ANC_WIRE_TRANSPARENCY = 0x03
        const val ANC_WIRE_ADAPTIVE = 0x04
    }

    private val supportedAncModes: List<AapSetting.AncMode.Value> by lazy {
        val features = model.features
        when {
            !features.hasAncControl -> emptyList()
            features.hasAdaptiveAnc -> listOf(AapSetting.AncMode.Value.ON, AapSetting.AncMode.Value.TRANSPARENCY, AapSetting.AncMode.Value.ADAPTIVE)
            else -> listOf(AapSetting.AncMode.Value.ON, AapSetting.AncMode.Value.TRANSPARENCY)
        }
    }

    override fun encodeHandshake(): ByteArray = byteArrayOf(
        0x00, 0x00, 0x04, 0x00, 0x01, 0x00, 0x02, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
    )

    override fun encodeNotificationEnable(): List<ByteArray> = listOf(
        byteArrayOf(0x04, 0x00, 0x04, 0x00, 0x0f, 0x00, 0xff.toByte(), 0xff.toByte(), 0xef.toByte(), 0xff.toByte()),
        byteArrayOf(0x04, 0x00, 0x04, 0x00, 0x0f, 0x00, 0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte()),
    )

    override fun encodeInitExt(): ByteArray? {
        if (!model.features.needsInitExt) return null
        return byteArrayOf(
            0x04, 0x00, 0x04, 0x00, 0x4d, 0x00, 0x0e, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        )
    }

    override fun encodeCommand(command: AapCommand): ByteArray = when (command) {
        is AapCommand.SetAncMode -> buildSettingsMessage(SETTING_ANC_MODE, encodeAncMode(command.mode))
        is AapCommand.SetConversationalAwareness -> buildSettingsMessage(SETTING_CONVERSATIONAL_AWARENESS, encodeAppleBool(command.enabled))
        is AapCommand.SetPressSpeed -> buildSettingsMessage(SETTING_PRESS_SPEED, command.value.wireValue)
        is AapCommand.SetPressHoldDuration -> buildSettingsMessage(SETTING_PRESS_HOLD_DURATION, command.value.wireValue)
        is AapCommand.SetNcWithOneAirPod -> buildSettingsMessage(SETTING_NC_ONE_AIRPOD, encodeAppleBool(command.enabled))
        is AapCommand.SetToneVolume -> buildSettingsMessage(SETTING_TONE_VOLUME, command.level.coerceIn(0x0F, 0x64))
        is AapCommand.SetVolumeSwipeLength -> buildSettingsMessage(SETTING_VOLUME_SWIPE_LENGTH, command.value.wireValue)
        is AapCommand.SetVolumeSwipe -> buildSettingsMessage(SETTING_VOLUME_SWIPE, encodeAppleBool(command.enabled))
        is AapCommand.SetPersonalizedVolume -> buildSettingsMessage(SETTING_PERSONALIZED_VOLUME, encodeAppleBool(command.enabled))
        is AapCommand.SetAdaptiveAudioNoise -> buildSettingsMessage(SETTING_ADAPTIVE_AUDIO_NOISE, command.level.coerceIn(0, 100))
        is AapCommand.SetEndCallMuteMic -> buildEndCallMuteMicMessage(command.muteMic, command.endCall)
    }

    override fun decodeSetting(message: AapMessage): Pair<KClass<out AapSetting>, AapSetting>? {
        // Ear detection is a separate command type (push-only from device)
        if (message.commandType == CMD_EAR_DETECTION) {
            if (message.payload.size < 2) return null
            return AapSetting.EarDetection::class to AapSetting.EarDetection(
                primaryPod = decodePodPlacement(message.payload[0].toInt() and 0xFF),
                secondaryPod = decodePodPlacement(message.payload[1].toInt() and 0xFF),
            )
        }

        // Conversation Awareness State is a separate command type (push-only)
        if (message.commandType == CMD_CONVERSATION_AWARENESS_STATE) {
            if (message.payload.isEmpty()) return null
            val value = message.payload[0].toInt() and 0xFF
            val speaking = value == 0x01
            return AapSetting.ConversationalAwarenessState::class to AapSetting.ConversationalAwarenessState(speaking)
        }

        if (message.commandType != CMD_SETTINGS) return null
        if (message.payload.size < 2) return null

        val settingId = message.payload[0].toInt() and 0xFF
        val value = message.payload[1].toInt() and 0xFF

        return when (settingId) {
            SETTING_ANC_MODE -> {
                val mode = decodeAncMode(value) ?: return null
                AapSetting.AncMode::class to AapSetting.AncMode(current = mode, supported = supportedAncModes)
            }
            SETTING_CONVERSATIONAL_AWARENESS -> {
                val enabled = decodeAppleBool(value) ?: return null
                AapSetting.ConversationalAwareness::class to AapSetting.ConversationalAwareness(enabled)
            }
            SETTING_PRESS_SPEED -> {
                val speed = AapSetting.PressSpeed.Value.fromWire(value) ?: return null
                AapSetting.PressSpeed::class to AapSetting.PressSpeed(speed)
            }
            SETTING_PRESS_HOLD_DURATION -> {
                val duration = AapSetting.PressHoldDuration.Value.fromWire(value) ?: return null
                AapSetting.PressHoldDuration::class to AapSetting.PressHoldDuration(duration)
            }
            SETTING_NC_ONE_AIRPOD -> {
                val enabled = decodeAppleBool(value) ?: return null
                AapSetting.NcWithOneAirPod::class to AapSetting.NcWithOneAirPod(enabled)
            }
            SETTING_TONE_VOLUME -> {
                AapSetting.ToneVolume::class to AapSetting.ToneVolume(level = value)
            }
            SETTING_VOLUME_SWIPE_LENGTH -> {
                val length = AapSetting.VolumeSwipeLength.Value.fromWire(value) ?: return null
                AapSetting.VolumeSwipeLength::class to AapSetting.VolumeSwipeLength(length)
            }
            SETTING_END_CALL_MUTE_MIC -> {
                decodeEndCallMuteMic(message.payload)
            }
            SETTING_VOLUME_SWIPE -> {
                val enabled = decodeAppleBool(value) ?: return null
                AapSetting.VolumeSwipe::class to AapSetting.VolumeSwipe(enabled)
            }
            SETTING_PERSONALIZED_VOLUME -> {
                val enabled = decodeAppleBool(value) ?: return null
                AapSetting.PersonalizedVolume::class to AapSetting.PersonalizedVolume(enabled)
            }
            SETTING_ADAPTIVE_AUDIO_NOISE -> {
                AapSetting.AdaptiveAudioNoise::class to AapSetting.AdaptiveAudioNoise(level = value)
            }
            else -> null
        }
    }

    override fun decodeBattery(message: AapMessage): Map<AapPodState.BatteryType, AapPodState.Battery>? {
        if (message.commandType != CMD_BATTERY) return null
        val payload = message.payload
        if (payload.isEmpty()) return null

        val count = payload[0].toInt() and 0xFF
        if (count == 0) return emptyMap()

        // Each battery entry is 5 bytes starting at offset 1
        val result = mutableMapOf<AapPodState.BatteryType, AapPodState.Battery>()
        var offset = 1
        for (i in 0 until count) {
            if (offset + 5 > payload.size) break

            val type = AapPodState.BatteryType.fromWire(payload[offset].toInt() and 0xFF) ?: run {
                offset += 5
                continue
            }
            // payload[offset+1] is unknown/reserved
            val percent = payload[offset + 2].toInt() and 0xFF
            val charging = AapPodState.ChargingState.fromWire(payload[offset + 3].toInt() and 0xFF)
            // payload[offset+4] is unknown/reserved
            offset += 5

            // Values above 100 are not valid battery percentages.
            // Known cases: 127 (0x7F) = fake reading after case close, 255 (0xFF) = disconnected.
            if (percent > 100) continue

            result[type] = AapPodState.Battery(
                type = type,
                percent = percent / 100f,
                charging = charging,
            )
        }

        return result
    }

    override fun encodePrivateKeyRequest(): ByteArray = byteArrayOf(
        0x04, 0x00, 0x04, 0x00, 0x30, 0x00, 0x05, 0x00
    )

    override fun decodePrivateKeyResponse(message: AapMessage): KeyExchangeResult? {
        if (message.commandType != CMD_PRIVATE_KEYS_RESPONSE) return null
        val payload = message.payload
        if (payload.isEmpty()) return null

        val keyCount = payload[0].toInt() and 0xFF
        var irk: ByteArray? = null
        var encKey: ByteArray? = null
        var offset = 1

        for (i in 0 until keyCount) {
            // Each entry: keyType(1), unknown(1), keyLength(1), unknown(1), keyData(keyLength)
            if (offset + 4 > payload.size) break
            val keyType = payload[offset].toInt() and 0xFF
            // offset+1 is unknown
            val keyLength = payload[offset + 2].toInt() and 0xFF
            // offset+3 is unknown
            offset += 4

            if (offset + keyLength > payload.size) break
            val keyData = payload.copyOfRange(offset, offset + keyLength)
            offset += keyLength

            when (keyType) {
                0x01 -> if (keyLength == 16) irk = keyData     // IRK
                0x04 -> if (keyLength == 16) encKey = keyData   // ENC
            }
        }

        return if (irk != null || encKey != null) KeyExchangeResult(irk, encKey) else null
    }

    override fun decodeDeviceInfo(message: AapMessage): AapDeviceInfo? {
        if (message.commandType != CMD_DEVICE_INFO) return null
        if (message.payload.size < 10) return null

        // Device info payload contains null-terminated ASCII strings
        // Format: [length prefix] [strings...]
        val strings = parseNullTerminatedStrings(message.payload)
        if (strings.size < 4) return null

        return AapDeviceInfo(
            name = strings.getOrElse(0) { "" },
            modelNumber = strings.getOrElse(1) { "" },
            manufacturer = strings.getOrElse(2) { "" },
            serialNumber = strings.getOrElse(3) { "" },
            firmwareVersion = strings.getOrElse(4) { "" },
        )
    }

    private fun decodePodPlacement(wireValue: Int): AapSetting.EarDetection.PodPlacement = when (wireValue) {
        0x00 -> AapSetting.EarDetection.PodPlacement.IN_EAR
        0x01 -> AapSetting.EarDetection.PodPlacement.NOT_IN_EAR
        0x02 -> AapSetting.EarDetection.PodPlacement.IN_CASE
        else -> AapSetting.EarDetection.PodPlacement.DISCONNECTED
    }

    protected fun encodeAncMode(mode: AapSetting.AncMode.Value): Int = when (mode) {
        AapSetting.AncMode.Value.OFF -> ANC_WIRE_OFF
        AapSetting.AncMode.Value.ON -> ANC_WIRE_ON
        AapSetting.AncMode.Value.TRANSPARENCY -> ANC_WIRE_TRANSPARENCY
        AapSetting.AncMode.Value.ADAPTIVE -> ANC_WIRE_ADAPTIVE
    }

    protected fun decodeAncMode(wireValue: Int): AapSetting.AncMode.Value? = when (wireValue) {
        ANC_WIRE_OFF -> AapSetting.AncMode.Value.OFF
        ANC_WIRE_ON -> AapSetting.AncMode.Value.ON
        ANC_WIRE_TRANSPARENCY -> AapSetting.AncMode.Value.TRANSPARENCY
        ANC_WIRE_ADAPTIVE -> AapSetting.AncMode.Value.ADAPTIVE
        else -> null
    }

    /** Standard Apple boolean encoding: 0x01=on, 0x02=off. */
    private fun encodeAppleBool(enabled: Boolean): Int = if (enabled) 0x01 else 0x02

    /** Decode Apple boolean: 0x01=true, 0x02=false, anything else=null (unknown). */
    private fun decodeAppleBool(wireValue: Int): Boolean? = when (wireValue) {
        0x01 -> true
        0x02 -> false
        else -> null
    }

    private fun buildSettingsMessage(settingId: Int, value: Int): ByteArray = byteArrayOf(
        0x04, 0x00, 0x04, 0x00,
        0x09, 0x00,
        settingId.toByte(), value.toByte(),
        0x00, 0x00, 0x00,
    )

    /** EndCallMuteMic uses a special 2-byte format: [0x24] [0x21] [muteMic] [endCall] [0x00] */
    private fun buildEndCallMuteMicMessage(muteMic: AapSetting.EndCallMuteMic.MuteMicMode, endCall: AapSetting.EndCallMuteMic.EndCallMode): ByteArray = byteArrayOf(
        0x04, 0x00, 0x04, 0x00,
        0x09, 0x00,
        SETTING_END_CALL_MUTE_MIC.toByte(), 0x21,
        muteMic.wireValue.toByte(), endCall.wireValue.toByte(),
        0x00,
    )

    private fun decodeEndCallMuteMic(payload: ByteArray): Pair<KClass<out AapSetting>, AapSetting>? {
        if (payload.size < 4) return null
        val subType = payload[1].toInt() and 0xFF
        return when (subType) {
            0x21 -> {
                // Standard format: byte 2 = muteMic, byte 3 = endCall
                val muteMic = AapSetting.EndCallMuteMic.MuteMicMode.fromWire(payload[2].toInt() and 0xFF) ?: return null
                val endCall = AapSetting.EndCallMuteMic.EndCallMode.fromWire(payload[3].toInt() and 0xFF) ?: return null
                AapSetting.EndCallMuteMic::class to AapSetting.EndCallMuteMic(muteMic, endCall)
            }
            0x00, 0x20 -> {
                // Alternate/compact response format: byte 2 is a combined mode value
                val combined = payload[2].toInt() and 0xFF
                val (muteMic, endCall) = when (combined) {
                    0x02 -> AapSetting.EndCallMuteMic.MuteMicMode.SINGLE_PRESS to AapSetting.EndCallMuteMic.EndCallMode.DOUBLE_PRESS
                    0x03 -> AapSetting.EndCallMuteMic.MuteMicMode.DOUBLE_PRESS to AapSetting.EndCallMuteMic.EndCallMode.SINGLE_PRESS
                    else -> return null
                }
                AapSetting.EndCallMuteMic::class to AapSetting.EndCallMuteMic(muteMic, endCall)
            }
            else -> null
        }
    }

    private fun parseNullTerminatedStrings(data: ByteArray): List<String> {
        val strings = mutableListOf<String>()
        var start = 0

        // Find runs of printable ASCII (0x20..0x7E) separated by null bytes.
        // Header bytes and non-ASCII bytes are skipped.
        var i = 0
        while (i < data.size) {
            val b = data[i].toInt() and 0xFF
            if (b in 0x20..0x7E) {
                start = i
                while (i < data.size && data[i] != 0x00.toByte()) i++
                strings.add(String(data, start, i - start, Charsets.US_ASCII))
            }
            i++
        }
        return strings
    }
}
