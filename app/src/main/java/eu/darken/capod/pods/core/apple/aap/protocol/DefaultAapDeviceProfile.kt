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
        /**
         * Catalogued control IDs we see on-wire but don't yet model as a named
         * [AapSetting] subclass. Decoded as [AapSetting.UnknownSetting] so
         * `lastMessageAt` still refreshes (freshness feeds AAP quality boost).
         * Promote an entry to a dedicated subclass when its semantics are
         * confirmed from captures.
         *
         * Observed on Pro 2 USB-C and/or Pro 3.
         */
        val UNMAPPED_SETTING_IDS = setOf(
            AapControlId.SELECTIVE_SPEECH_LISTENING.value, // 0x29
            AapControlId.HEARING_AID.value,                 // 0x2C
            AapControlId.HEARING_AID_GAIN_SWIPE.value,      // 0x2F
            AapControlId.HEART_RATE_MONITOR_3.value,        // 0x30
            AapControlId.HEARING_ASSIST.value,              // 0x33
            AapControlId.HEARING_PROTECTION_PPE.value,      // 0x37
            AapControlId.PPE_CAP_LEVEL_CONFIG.value,        // 0x38
            AapControlId.UPLINK_EQ_BUD.value,               // 0x3E
        )

        // ANC mode wire values
        const val ANC_WIRE_OFF = 0x01
        const val ANC_WIRE_ON = 0x02
        const val ANC_WIRE_TRANSPARENCY = 0x03
        const val ANC_WIRE_ADAPTIVE = 0x04

        /** Fixed length of each earbud UUID segment in the Information payload (segments 11 & 12). */
        private const val UUID_LEN = 17

        /**
         * Models where sending a 0x22 CASE_INFO_REQUEST yields a 0x23 response.
         * Grow this list as captures confirm other models respond correctly.
         */
        private val CASE_INFO_ALLOWLIST = setOf(
            PodModel.AIRPODS_PRO3,
        )
    }

    private val supportedAncModes: List<AapSetting.AncMode.Value> by lazy {
        val features = model.features
        when {
            !features.hasAncControl -> emptyList()
            features.hasAdaptiveAnc -> listOf(AapSetting.AncMode.Value.OFF, AapSetting.AncMode.Value.ON, AapSetting.AncMode.Value.TRANSPARENCY, AapSetting.AncMode.Value.ADAPTIVE)
            else -> listOf(AapSetting.AncMode.Value.OFF, AapSetting.AncMode.Value.ON, AapSetting.AncMode.Value.TRANSPARENCY)
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

    override fun encodeInitExt(): ByteArray = byteArrayOf(
        0x04, 0x00, 0x04, 0x00, 0x4d, 0x00, 0xd7.toByte(), 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    )

    override fun encodeCommand(command: AapCommand): ByteArray = when (command) {
        is AapCommand.SetAncMode -> buildSettingsMessage(AapControlId.LISTEN_MODE.value, encodeAncMode(command.mode))
        is AapCommand.SetConversationalAwareness -> buildSettingsMessage(AapControlId.CONVERSATION_DETECT.value, encodeAppleBool(command.enabled))
        is AapCommand.SetPressSpeed -> buildSettingsMessage(AapControlId.DOUBLE_CLICK_INTERVAL.value, command.value.wireValue)
        is AapCommand.SetPressHoldDuration -> buildSettingsMessage(AapControlId.CLICK_AND_HOLD_INTERVAL.value, command.value.wireValue)
        is AapCommand.SetNcWithOneAirPod -> buildSettingsMessage(AapControlId.ONE_BUD_ANC_MODE.value, encodeAppleBool(command.enabled))
        is AapCommand.SetToneVolume -> buildSettingsMessage(AapControlId.CHIME_VOLUME.value, command.level.coerceIn(0x0F, 0x64))
        is AapCommand.SetVolumeSwipeLength -> buildSettingsMessage(AapControlId.VOLUME_SWIPE_INTERVAL.value, command.value.wireValue)
        is AapCommand.SetVolumeSwipe -> buildSettingsMessage(AapControlId.VOLUME_SWIPE_MODE.value, encodeAppleBool(command.enabled))
        is AapCommand.SetPersonalizedVolume -> buildSettingsMessage(AapControlId.ADAPTIVE_VOLUME.value, encodeAppleBool(command.enabled))
        // Wire semantics are inverted: wire 0 = max noise reduction, wire 100 = min (transparency-like).
        // UI value 0..100 follows user intuition (100 = max NC), so flip on write/read.
        is AapCommand.SetAdaptiveAudioNoise -> buildSettingsMessage(AapControlId.AUTO_ANC_STRENGTH.value, 100 - command.level.coerceIn(0, 100))
        is AapCommand.SetEndCallMuteMic -> buildEndCallMuteMicMessage(command.muteMic, command.endCall)
        is AapCommand.SetMicrophoneMode -> buildSettingsMessage(AapControlId.MIC_MODE.value, command.mode.wireValue)
        is AapCommand.SetEarDetectionEnabled -> buildSettingsMessage(AapControlId.IN_EAR_DETECTION.value, encodeAppleBool(command.enabled))
        is AapCommand.SetListeningModeCycle -> buildSettingsMessage(AapControlId.LISTENING_MODE_CONFIGS.value, command.modeMask and 0x0F)
        is AapCommand.SetAllowOffOption -> buildSettingsMessage(AapControlId.ALLOW_OFF_OPTION.value, encodeAppleBool(command.enabled))
        is AapCommand.SetStemConfig -> buildSettingsMessage(AapControlId.RAW_GESTURES_CONFIG.value, command.claimedPressMask and 0x0F)
        is AapCommand.SetSleepDetection -> buildSettingsMessage(AapControlId.SLEEP_DETECTION.value, encodeAppleBool(command.enabled))
        is AapCommand.SetDynamicEndOfCharge -> buildSettingsMessage(AapControlId.DYNAMIC_END_OF_CHARGE.value, encodeAppleBool(command.enabled))
        is AapCommand.SetDeviceName -> buildRenameMessage(command.name)
    }

    override fun decodeSetting(message: AapMessage): Pair<KClass<out AapSetting>, AapSetting>? {
        // Primary pod identity (push-only, fires on mic/primary swap)
        if (message.commandType == AapMessageType.BUD_ROLE.value) {
            if (message.payload.size < 4) return null
            val podId = message.payload[0].toInt() and 0xFF
            // Validate known fixed bytes: [podId] 00 [00|01] [00|01]
            // Pro 3: byte[2]=0x01 always. Pro 1: byte[2]=0x00 on initial, 0x01 on swap.
            if ((message.payload[1].toInt() and 0xFF) != 0x00) return null
            val byte2 = message.payload[2].toInt() and 0xFF
            if (byte2 != 0x00 && byte2 != 0x01) return null
            val pod = when (podId) {
                0x01 -> AapSetting.PrimaryPod.Pod.LEFT
                0x02 -> AapSetting.PrimaryPod.Pod.RIGHT
                else -> return null
            }
            return AapSetting.PrimaryPod::class to AapSetting.PrimaryPod(pod)
        }

        // Ear detection is a separate command type (push-only from device)
        if (message.commandType == AapMessageType.EAR_DETECTION.value) {
            if (message.payload.size < 2) return null
            return AapSetting.EarDetection::class to AapSetting.EarDetection(
                primaryPod = decodePodPlacement(message.payload[0].toInt() and 0xFF),
                secondaryPod = decodePodPlacement(message.payload[1].toInt() and 0xFF),
            )
        }

        // Connected devices list (push-only from device)
        if (message.commandType == AapMessageType.CONNECTED_DEVICES.value) {
            if (message.payload.size < 3) return null
            val count = message.payload[2].toInt() and 0xFF
            val devices = mutableListOf<AapSetting.ConnectedDevices.ConnectedDevice>()
            var offset = 3
            for (i in 0 until count) {
                if (offset + 8 > message.payload.size) break
                val mac = (0 until 6).map { "%02X".format(message.payload[offset + it]) }.joinToString(":")
                val type = message.payload[offset + 6].toInt() and 0xFF
                devices.add(AapSetting.ConnectedDevices.ConnectedDevice(mac, type))
                offset += 8
            }
            return AapSetting.ConnectedDevices::class to AapSetting.ConnectedDevices(devices)
        }

        // Audio source tracking (push-only from device)
        if (message.commandType == AapMessageType.AUDIO_SOURCE.value) {
            if (message.payload.size < 7) return null
            val mac = (0 until 6).map { "%02X".format(message.payload[it]) }.joinToString(":")
            val typeValue = message.payload[6].toInt() and 0xFF
            val type = when (typeValue) {
                0x01 -> AapSetting.AudioSource.AudioSourceType.CALL
                0x02 -> AapSetting.AudioSource.AudioSourceType.MEDIA
                else -> AapSetting.AudioSource.AudioSourceType.NONE
            }
            return AapSetting.AudioSource::class to AapSetting.AudioSource(mac, type)
        }

        // 0x53 is "PME Config" per the Wireshark AAP dissector — Personal Medical
        // Equipment (cf. PPE), i.e. the iOS 18.1+ hearing-aid profile on AirPods
        // Pro 2. Decoded verbatim as 4 × 8 Float32 (per-ear × per-profile band
        // gains); stock firmware reports all-zero until the user runs Apple's
        // Hearing Test. 0x54 "Set Band Edges" is a neighbouring opcode with a
        // different payload — not decoded here.
        if (message.commandType == AapMessageType.PME_CONFIG.value) {
            if (message.payload.size < 6 + 128) return null
            val sets = mutableListOf<List<Float>>()
            var offset = 6 // skip header
            for (s in 0 until 4) {
                val bands = mutableListOf<Float>()
                for (b in 0 until 8) {
                    val bits = (message.payload[offset].toInt() and 0xFF) or
                        ((message.payload[offset + 1].toInt() and 0xFF) shl 8) or
                        ((message.payload[offset + 2].toInt() and 0xFF) shl 16) or
                        ((message.payload[offset + 3].toInt() and 0xFF) shl 24)
                    bands.add(Float.fromBits(bits))
                    offset += 4
                }
                sets.add(bands)
            }
            return AapSetting.PmeConfig::class to AapSetting.PmeConfig(sets)
        }

        // Conversation Awareness State is a separate command type (push-only)
        if (message.commandType == AapMessageType.CONVERSATIONAL_AWARENESS.value) {
            if (message.payload.isEmpty()) return null
            val value = message.payload[0].toInt() and 0xFF
            val speaking = value == 0x01
            return AapSetting.ConversationalAwarenessState::class to AapSetting.ConversationalAwarenessState(speaking)
        }

        if (message.commandType != AapMessageType.CONTROL.value) return null
        if (message.payload.size < 2) return null

        val settingId = message.payload[0].toInt() and 0xFF
        val value = message.payload[1].toInt() and 0xFF

        return when (settingId) {
            AapControlId.LISTEN_MODE.value -> {
                val mode = decodeAncMode(value) ?: return null
                AapSetting.AncMode::class to AapSetting.AncMode(current = mode, supported = supportedAncModes)
            }
            AapControlId.CONVERSATION_DETECT.value -> {
                val enabled = decodeAppleBool(value) ?: return null
                AapSetting.ConversationalAwareness::class to AapSetting.ConversationalAwareness(enabled)
            }
            AapControlId.DOUBLE_CLICK_INTERVAL.value -> {
                val speed = AapSetting.PressSpeed.Value.fromWire(value) ?: return null
                AapSetting.PressSpeed::class to AapSetting.PressSpeed(speed)
            }
            AapControlId.CLICK_AND_HOLD_INTERVAL.value -> {
                val duration = AapSetting.PressHoldDuration.Value.fromWire(value) ?: return null
                AapSetting.PressHoldDuration::class to AapSetting.PressHoldDuration(duration)
            }
            AapControlId.ONE_BUD_ANC_MODE.value -> {
                val enabled = decodeAppleBool(value) ?: return null
                AapSetting.NcWithOneAirPod::class to AapSetting.NcWithOneAirPod(enabled)
            }
            AapControlId.CHIME_VOLUME.value -> {
                AapSetting.ToneVolume::class to AapSetting.ToneVolume(level = value)
            }
            AapControlId.VOLUME_SWIPE_INTERVAL.value -> {
                val length = AapSetting.VolumeSwipeLength.Value.fromWire(value) ?: return null
                AapSetting.VolumeSwipeLength::class to AapSetting.VolumeSwipeLength(length)
            }
            AapControlId.CALL_MANAGEMENT_CONFIG.value -> {
                decodeEndCallMuteMic(message.payload)
            }
            AapControlId.VOLUME_SWIPE_MODE.value -> {
                val enabled = decodeAppleBool(value) ?: return null
                AapSetting.VolumeSwipe::class to AapSetting.VolumeSwipe(enabled)
            }
            AapControlId.ADAPTIVE_VOLUME.value -> {
                // Wireshark calls 0x26 "Adaptive Volume"; CAPod ships the user-facing name
                // "Personalized Volume" because that matches the iOS Settings.app label.
                val enabled = decodeAppleBool(value) ?: return null
                AapSetting.PersonalizedVolume::class to AapSetting.PersonalizedVolume(enabled)
            }
            AapControlId.AUTO_ANC_STRENGTH.value -> {
                AapSetting.AdaptiveAudioNoise::class to AapSetting.AdaptiveAudioNoise(level = 100 - value.coerceIn(0, 100))
            }
            AapControlId.MIC_MODE.value -> {
                val mode = AapSetting.MicrophoneMode.Mode.fromWire(value) ?: return null
                AapSetting.MicrophoneMode::class to AapSetting.MicrophoneMode(mode)
            }
            AapControlId.IN_EAR_DETECTION.value -> {
                val enabled = decodeAppleBool(value) ?: return null
                AapSetting.EarDetectionEnabled::class to AapSetting.EarDetectionEnabled(enabled)
            }
            AapControlId.LISTENING_MODE_CONFIGS.value -> {
                AapSetting.ListeningModeCycle::class to AapSetting.ListeningModeCycle(modeMask = value)
            }
            AapControlId.ALLOW_OFF_OPTION.value -> {
                val enabled = decodeAppleBool(value) ?: return null
                AapSetting.AllowOffOption::class to AapSetting.AllowOffOption(enabled)
            }
            AapControlId.RAW_GESTURES_CONFIG.value -> {
                AapSetting.StemConfig::class to AapSetting.StemConfig(claimedPressMask = value)
            }
            AapControlId.SLEEP_DETECTION.value -> {
                val enabled = decodeAppleBool(value) ?: return null
                AapSetting.SleepDetection::class to AapSetting.SleepDetection(enabled)
            }
            AapControlId.DYNAMIC_END_OF_CHARGE.value -> {
                // Apple's "Optimized Charge Limit" — Pro 3 pushes this on connect as value 0x01
                // (enabled). decodeAppleBool rejects anything that isn't a confirmed bool so
                // unknown encodings fall through to UnknownSetting logging rather than being
                // coerced to false.
                val enabled = decodeAppleBool(value) ?: return null
                AapSetting.DynamicEndOfCharge::class to AapSetting.DynamicEndOfCharge(enabled)
            }
            AapControlId.IN_CASE_TONE.value -> {
                // Decoded internally (never exposed in UI) to keep lastMessageAt fresh.
                // Originally labeled "Charging Sounds" — the real case tones are controlled
                // over ATT, not here; the actual effect of this setting is unknown.
                val enabled = decodeAppleBool(value) ?: return null
                AapSetting.InCaseTone::class to AapSetting.InCaseTone(enabled)
            }
            in UNMAPPED_SETTING_IDS -> {
                AapSetting.UnknownSetting::class to AapSetting.UnknownSetting(
                    settingId = settingId,
                    rawValue = value,
                )
            }
            else -> null
        }
    }

    override fun decodeBattery(message: AapMessage): Map<AapPodState.BatteryType, AapPodState.Battery>? {
        if (message.commandType != AapMessageType.BATTERY_INFO.value) return null
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
        if (message.commandType != AapMessageType.MAGIC_KEYS.value) return null
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
        if (message.commandType != AapMessageType.INFORMATION.value) return null
        if (message.payload.size < 10) return null

        val parsed = parseDeviceInfoPayload(message.payload) ?: return null
        if (parsed.strings.size < 4) return null

        val activeFirmware = parsed.strings.getOrElse(4) { "" }
        val pendingFirmware = parsed.strings.getOrNull(5)?.takeIf { it.isNotBlank() && it != activeFirmware }

        return AapDeviceInfo(
            name = parsed.strings.getOrElse(0) { "" },
            modelNumber = parsed.strings.getOrElse(1) { "" },
            manufacturer = parsed.strings.getOrElse(2) { "" },
            serialNumber = parsed.strings.getOrElse(3) { "" },
            firmwareVersion = activeFirmware,
            firmwareVersionPending = pendingFirmware,
            hardwareVersion = parsed.strings.getOrNull(6)?.takeIf { it.isNotBlank() },
            eaProtocolName = parsed.strings.getOrNull(7)?.takeIf { it.isNotBlank() },
            leftEarbudSerial = parsed.strings.getOrNull(8)?.takeIf { it.isNotBlank() },
            rightEarbudSerial = parsed.strings.getOrNull(9)?.takeIf { it.isNotBlank() },
            marketingVersion = parsed.strings.getOrNull(10)?.takeIf { it.isNotBlank() },
            leftEarbudUuid = parsed.leftEarbudUuid,
            rightEarbudUuid = parsed.rightEarbudUuid,
            leftEarbudFirstPaired = parsed.leftEarbudFirstPaired,
            rightEarbudFirstPaired = parsed.rightEarbudFirstPaired,
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

    /**
     * EndCallMuteMic write uses compact format [0x24] [0x20] [combined] [0x00] [0x00].
     * The LibrePods-documented 0x21 "standard" format is silently ignored by real firmware
     * — no captured session (Pro 1, Pro 2 USB-C, Pro 3) has ever emitted it. Real devices
     * emit subtype 0x20 or 0x00; writes using 0x20 are accepted and persisted.
     * Combined-byte mapping is based on real-device correlation, not the older third-party docs:
     * on AirPods Pro 2 USB-C, compact value 0x03 matched the macOS UI state
     * "Single press to mute microphone / Double press to end call" (2026-04-15 capture).
     */
    private fun buildEndCallMuteMicMessage(
        muteMic: AapSetting.EndCallMuteMic.MuteMicMode,
        endCall: AapSetting.EndCallMuteMic.EndCallMode,
    ): ByteArray {
        val combined = when {
            muteMic == AapSetting.EndCallMuteMic.MuteMicMode.SINGLE_PRESS &&
                    endCall == AapSetting.EndCallMuteMic.EndCallMode.DOUBLE_PRESS -> 0x03
            muteMic == AapSetting.EndCallMuteMic.MuteMicMode.DOUBLE_PRESS &&
                    endCall == AapSetting.EndCallMuteMic.EndCallMode.SINGLE_PRESS -> 0x02
            else -> error("SetEndCallMuteMic invariant violated (validated by AapCommand.init)")
        }
        return byteArrayOf(
            0x04, 0x00, 0x04, 0x00,
            0x09, 0x00,
            AapControlId.CALL_MANAGEMENT_CONFIG.value.toByte(), 0x20,
            combined.toByte(),
            0x00, 0x00,
        )
    }

    private fun decodeEndCallMuteMic(payload: ByteArray): Pair<KClass<out AapSetting>, AapSetting>? {
        if (payload.size < 4) return null
        val subType = payload[1].toInt() and 0xFF
        return when (subType) {
            0x21 -> {
                // Legacy doc-sourced format (LibrePods/MagicPodsCore). Never observed in real captures.
                // Kept for forward-compat: if any unknown firmware emits this, decode still works.
                // NOTE: writes must use compact 0x20 format — real devices silently ignore 0x21.
                val muteMic = AapSetting.EndCallMuteMic.MuteMicMode.fromWire(payload[2].toInt() and 0xFF) ?: return null
                val endCall = AapSetting.EndCallMuteMic.EndCallMode.fromWire(payload[3].toInt() and 0xFF) ?: return null
                AapSetting.EndCallMuteMic::class to AapSetting.EndCallMuteMic(muteMic, endCall)
            }
            0x00, 0x20 -> {
                // Compact response format: byte 2 is a combined mode value.
                // 0x00: observed on Pro 1 (fw 51.9.6, 2026-04-02) and Pro 2 USB-C (fw 81.x, 2026-04-02) and Pro 3.
                // 0x20: observed on Pro 1 (fw 51.9.6, 2026-03-31). Same device can send either subtype across sessions.
                val combined = payload[2].toInt() and 0xFF
                val (muteMic, endCall) = when (combined) {
                    0x02 -> AapSetting.EndCallMuteMic.MuteMicMode.DOUBLE_PRESS to AapSetting.EndCallMuteMic.EndCallMode.SINGLE_PRESS
                    0x03 -> AapSetting.EndCallMuteMic.MuteMicMode.SINGLE_PRESS to AapSetting.EndCallMuteMic.EndCallMode.DOUBLE_PRESS
                    else -> return null
                }
                AapSetting.EndCallMuteMic::class to AapSetting.EndCallMuteMic(muteMic, endCall)
            }
            else -> null
        }
    }

    override fun decodeStemPress(message: AapMessage): StemPressEvent? {
        if (message.commandType != AapMessageType.STEM_PRESS.value) return null
        if (message.payload.size < 2) return null
        val pressType = StemPressEvent.PressType.fromWire(message.payload[0].toInt() and 0xFF) ?: return null
        val bud = StemPressEvent.Bud.fromWire(message.payload[1].toInt() and 0xFF) ?: return null
        return StemPressEvent(pressType, bud)
    }

    /**
     * Case Info probing is allowlisted to models observed to respond. Pro 3 is the
     * confirmed first entry; other models can be added once captures verify they
     * reply to 0x22 with a 0x23 payload.
     */
    override fun encodeCaseInfoRequest(): ByteArray? {
        if (model !in CASE_INFO_ALLOWLIST) return null
        // Fire-and-forget 6-byte template, matching the private key request shape
        // (header + command type with no payload).
        return byteArrayOf(
            0x04, 0x00, 0x04, 0x00,
            AapMessageType.CASE_INFO_REQUEST.value.toByte(), 0x00,
        )
    }

    /**
     * Best-effort decoder. Per the dissector, the payload contains a mix of
     * binary VID/PID/color bytes plus a NUL-delimited name string. Without
     * more captures we preserve the raw payload and leave individual fields
     * null — future iteration can map specific offsets.
     */
    override fun decodeCaseInfo(message: AapMessage): AapCaseInfo? {
        if (message.commandType != AapMessageType.CASE_INFO.value) return null
        return AapCaseInfo(rawPayload = message.payload.copyOf())
    }

    override fun decodeSleepEvent(message: AapMessage): AapSleepEvent? {
        if (message.commandType != AapMessageType.SLEEP_DETECTION_UPDATE.value) return null
        return AapSleepEvent(rawPayload = message.payload.copyOf())
    }

    override fun decodeDynamicEndOfChargeEvent(message: AapMessage): AapDynamicEndOfChargeEvent? {
        if (message.commandType != AapMessageType.DYNAMIC_END_OF_CHARGE.value) return null
        return AapDynamicEndOfChargeEvent(rawPayload = message.payload.copyOf())
    }

    private fun buildRenameMessage(name: String): ByteArray {
        // Uses the opcode 0x1A format from the LibrePods AAP docs + Linux implementation.
        // Verified end-to-end on AirPods Pro 2 USB-C (firmware 81.2675...): the device accepts
        // the rename, persists it, and echoes the new name back via the next 0x001D device info
        // message on reconnect.
        //
        // Note: the LibrePods Android code (AACPManager.createRenamePacket) uses a DIFFERENT
        // format with opcode 0x1E and a trailing NUL, but on-device testing shows the device
        // silently ignores that variant. The Linux / documented format is the one that works.
        //
        // Scope: this only changes the AirPods firmware's self-reported name (what 0x001D
        // returns). It does NOT update the phone's Bluetooth alias — Android's system Bluetooth
        // settings read from the bond database, which is separate and would require
        // BluetoothDevice.setAlias(). That's intentionally out of scope here.
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        require(nameBytes.size <= 127) { "Device name too long: ${nameBytes.size} bytes (max 127)" }
        return byteArrayOf(
            0x04, 0x00, 0x04, 0x00,
            0x1A, 0x00, 0x01,
            nameBytes.size.toByte(), 0x00,
        ) + nameBytes
    }

    /**
     * Heuristic binary-prefix skip: walk forward until we hit a printable byte.
     * The real header schema is `[02 XX 00 04 00]` in every capture to date, but
     * documenting that without an authoritative source would be guessing. The
     * heuristic breaks only for device names that start with a non-printable
     * byte \u2014 theoretically emoji names (UTF-8 first byte 0xF0-0xF4, outside the
     * printable range) could misalign here. None observed in real captures so far.
     */
    private fun skipBinaryHeader(data: ByteArray): Int {
        var offset = 0
        while (offset < data.size) {
            val b = data[offset].toInt() and 0xFF
            if (b in 0x20..0x7E) return offset
            offset++
        }
        return data.size
    }

    private fun parseDeviceInfoPayload(data: ByteArray): DeviceInfoSegments? {
        var offset = skipBinaryHeader(data)
        if (offset >= data.size) return null

        // Segments 0..10: NUL-delimited UTF-8 strings. UTF-8 means non-ASCII (e.g.
        // curly quotes in "Matthias's AirPods Pro") decodes correctly.
        val strings = mutableListOf<String>()
        while (strings.size < 11 && offset < data.size) {
            while (offset < data.size && data[offset] == 0x00.toByte()) offset++
            if (offset >= data.size) break
            val segStart = offset
            while (offset < data.size && data[offset] != 0x00.toByte()) offset++
            strings.add(String(data, segStart, offset - segStart, Charsets.UTF_8))
        }

        // Skip the NUL that terminated segment 10 (if present) before the UUID blob.
        while (offset < data.size && data[offset] == 0x00.toByte()) offset++

        // Segments 11 and 12: fixed 17-byte UUIDs (NOT NUL-terminated, may contain 0x00).
        // Best-effort: if payload is shorter, both UUIDs are null.
        val leftUuid: ByteArray? = if (offset + UUID_LEN <= data.size) {
            val slice = data.copyOfRange(offset, offset + UUID_LEN)
            offset += UUID_LEN
            slice
        } else null

        val rightUuid: ByteArray? = if (offset + UUID_LEN <= data.size) {
            val slice = data.copyOfRange(offset, offset + UUID_LEN)
            offset += UUID_LEN
            slice
        } else null

        // Segments 13 and 14: NUL-delimited ASCII-decimal epoch seconds (e.g. "1697480211").
        val trailingStrings = mutableListOf<String>()
        while (trailingStrings.size < 2 && offset < data.size) {
            while (offset < data.size && data[offset] == 0x00.toByte()) offset++
            if (offset >= data.size) break
            val segStart = offset
            while (offset < data.size && data[offset] != 0x00.toByte()) offset++
            trailingStrings.add(String(data, segStart, offset - segStart, Charsets.UTF_8))
        }

        val leftPaired = trailingStrings.getOrNull(0).parseEpochSecondsOrNull()
        val rightPaired = trailingStrings.getOrNull(1).parseEpochSecondsOrNull()

        return DeviceInfoSegments(
            strings = strings,
            leftEarbudUuid = leftUuid,
            rightEarbudUuid = rightUuid,
            leftEarbudFirstPaired = leftPaired,
            rightEarbudFirstPaired = rightPaired,
        )
    }

    private fun String?.parseEpochSecondsOrNull(): java.time.Instant? {
        if (this.isNullOrBlank()) return null
        val seconds = this.toLongOrNull() ?: return null
        return runCatching { java.time.Instant.ofEpochSecond(seconds) }.getOrNull()
    }

    private data class DeviceInfoSegments(
        val strings: List<String>,
        val leftEarbudUuid: ByteArray?,
        val rightEarbudUuid: ByteArray?,
        val leftEarbudFirstPaired: java.time.Instant?,
        val rightEarbudFirstPaired: java.time.Instant?,
    )
}
