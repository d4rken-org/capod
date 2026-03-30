package eu.darken.capod.pods.core.apple.protocol.aap

import kotlin.reflect.KClass

/**
 * Default AAP device profile covering the known protocol from MagicPodsCore + PoC captures.
 * Handles the common wire format used by AirPods Pro 2, Pro 3, and similar H2/H3 chip devices.
 *
 * When model-specific differences are discovered, subclass and override the relevant methods.
 */
class DefaultAapDeviceProfile : AapDeviceProfile {

    companion object {
        // AAP command types (bytes 4-5 of the message, little-endian)
        const val CMD_SETTINGS = 0x0009
        const val CMD_DEVICE_INFO = 0x001D

        // Setting IDs (first byte of settings command payload)
        const val SETTING_ANC_MODE = 0x0D
        const val SETTING_CONVERSATIONAL_AWARENESS = 0x18

        // ANC mode wire values
        const val ANC_WIRE_OFF = 0x01
        const val ANC_WIRE_ON = 0x02
        const val ANC_WIRE_TRANSPARENCY = 0x03
        const val ANC_WIRE_ADAPTIVE = 0x04

        // Default supported ANC modes
        val DEFAULT_ANC_MODES = listOf(AncModeValue.ON, AncModeValue.TRANSPARENCY, AncModeValue.ADAPTIVE)
    }

    override fun encodeHandshake(): ByteArray = byteArrayOf(
        0x00, 0x00, 0x04, 0x00, 0x01, 0x00, 0x02, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
    )

    override fun encodeCommand(command: AapCommand): ByteArray = when (command) {
        is AapCommand.SetAncMode -> buildSettingsMessage(
            SETTING_ANC_MODE,
            encodeAncMode(command.mode)
        )
        is AapCommand.SetConversationalAwareness -> buildSettingsMessage(
            SETTING_CONVERSATIONAL_AWARENESS,
            if (command.enabled) 0x01 else 0x00
        )
    }

    override fun decodeSetting(message: AapMessage): Pair<KClass<out AapSetting>, AapSetting>? {
        if (message.commandType != CMD_SETTINGS) return null
        if (message.payload.size < 2) return null

        val settingId = message.payload[0].toInt() and 0xFF
        val value = message.payload[1].toInt() and 0xFF

        return when (settingId) {
            SETTING_ANC_MODE -> {
                val mode = decodeAncMode(value) ?: return null
                AapSetting.AncMode::class to AapSetting.AncMode(
                    current = mode,
                    supported = DEFAULT_ANC_MODES,
                )
            }
            SETTING_CONVERSATIONAL_AWARENESS -> {
                AapSetting.ConversationalAwareness::class to AapSetting.ConversationalAwareness(
                    enabled = value != 0,
                )
            }
            else -> null
        }
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

    protected fun encodeAncMode(mode: AncModeValue): Int = when (mode) {
        AncModeValue.OFF -> ANC_WIRE_OFF
        AncModeValue.ON -> ANC_WIRE_ON
        AncModeValue.TRANSPARENCY -> ANC_WIRE_TRANSPARENCY
        AncModeValue.ADAPTIVE -> ANC_WIRE_ADAPTIVE
    }

    protected fun decodeAncMode(wireValue: Int): AncModeValue? = when (wireValue) {
        ANC_WIRE_OFF -> AncModeValue.OFF
        ANC_WIRE_ON -> AncModeValue.ON
        ANC_WIRE_TRANSPARENCY -> AncModeValue.TRANSPARENCY
        ANC_WIRE_ADAPTIVE -> AncModeValue.ADAPTIVE
        else -> null
    }

    private fun buildSettingsMessage(settingId: Int, value: Int): ByteArray = byteArrayOf(
        0x04, 0x00, 0x04, 0x00,
        0x09, 0x00,
        settingId.toByte(), value.toByte(),
        0x00, 0x00, 0x00,
    )

    private fun parseNullTerminatedStrings(data: ByteArray): List<String> {
        val strings = mutableListOf<String>()
        var start = 0
        // Skip initial length/flags bytes (first few bytes before string data)
        val stringDataStart = data.indexOfFirst { it == 0x00.toByte() && data.indexOf(it.toByte()) > 2 }
            .takeIf { it >= 0 } ?: return strings

        // Find runs of printable ASCII separated by null bytes
        var i = 0
        while (i < data.size) {
            if (data[i] != 0x00.toByte() && data[i].toInt() and 0xFF >= 0x20) {
                start = i
                while (i < data.size && data[i] != 0x00.toByte()) i++
                strings.add(String(data, start, i - start, Charsets.US_ASCII))
            }
            i++
        }
        return strings
    }
}
