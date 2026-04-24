package eu.darken.capod.pods.core.apple.aap.protocol

/**
 * Catalog of AAP (Apple Accessory Protocol, also "AACP") message types —
 * the 16-bit opcode at bytes 4-5 of a Message-type packet (packet type 0x04).
 *
 * Sources:
 *  - https://github.com/pabloaul/apple-wireshark/blob/main/plugins/aacp.lua
 *  - LibrePods / MagicPodsCore
 *  - Real device captures (AirPods Pro 1, Pro 2 USB-C, Pro 3)
 *
 * Entries whose names start with `UNKNOWN_` exist in Wireshark's dissector
 * but have no known meaning. They're catalogued here so unhandled-message
 * logging still reports a named opcode instead of a bare int.
 */
enum class AapMessageType(val value: Int, val wiresharkName: String) {
    CAPABILITIES_REQUEST(0x0001, "Capabilities Request"),
    CAPABILITIES(0x0002, "Capabilities"),
    BATTERY_INFO_REQUEST(0x0003, "Battery Info Request"),
    BATTERY_INFO(0x0004, "Battery Info"),
    EAR_DETECTION_REQUEST(0x0005, "Ear Detection Request"),
    EAR_DETECTION(0x0006, "Ear Detection"),
    BUD_ROLE_REQUEST(0x0007, "Bud Role Request"),
    BUD_ROLE(0x0008, "Bud Role"),

    /** Controls / settings. The control ID is payload byte 0 — see [AapControlId]. */
    CONTROL(0x0009, "Control"),

    DEVICE_LIST(0x000B, "Device List"),
    MAC_ADDRESS(0x000C, "MAC Address"),
    STREAM_STATE_INFO_REQUEST(0x000D, "Stream State Info Request"),
    AUDIO_SOURCE(0x000E, "Audio Source"),
    SET_NOTIFICATION_FILTER(0x000F, "Set Notification Filter"),
    SMART_ROUTING_1(0x0010, "Smart Routing"),
    SMART_ROUTING_2(0x0011, "Smart Routing"),
    EASY_PAIR_REQUEST(0x0012, "Easy Pair Request?"),
    CONNECT_PRIORITY_LIST(0x0014, "Connect Priority List"),
    TRIANGLE_STATUS_REQUEST(0x0015, "Triangle Status Request"),
    MAGNET_LINK(0x0016, "Magnet Link"),

    /**
     * Buddy Command per Wireshark. In CAPod we treat payloads of this opcode as
     * HID descriptor frames (Service Directory / descriptor / terminator — see [HidTracker]).
     * Both interpretations may be correct for different sub-payloads.
     */
    BUDDY_COMMAND(0x0017, "BuddyCommand"),

    STEM_PRESS(0x0019, "Stem Press"),
    RENAME(0x001A, "Rename"),
    TIMESTAMP(0x001B, "Timestamp"),
    INFORMATION(0x001D, "Information"),
    SEND_EXTERNAL_ACCESSORY_SESSION_PACKET(0x001E, "Send External Accessory Session Packet"),
    NOTIFY_SESSION_STATE(0x001F, "Notify Session State?"),
    SEND_REMOTE_FIRMWARE_AUTH_DATA(0x0020, "Send Remote Firmware Auth Data"),
    UNKNOWN_0X21(0x0021, "Unknown"),
    CASE_INFO_REQUEST(0x0022, "Case Info Request"),
    CASE_INFO(0x0023, "Case Info"),
    SEND_DEVICE_INFO(0x0024, "Send Device Info?"),
    CERTIFICATES_REQUEST(0x0026, "Certificates Request"),
    CERTIFICATES(0x0027, "Certificates"),
    GYRO_INFO(0x0028, "Gyro Info"),
    SET_COUNTRY_CODE(0x0029, "Set Country Code"),
    STREAM_STATE_INFO(0x002B, "Stream State Info"),
    GAPA_CHALLENGE(0x002C, "GAPA Challenge"),
    CONNECTED_DEVICES_REQUEST(0x002D, "Connected Devices Request"),
    CONNECTED_DEVICES(0x002E, "Connected Devices"),
    MAGIC_KEYS_REQUEST(0x0030, "Magic Keys Request"),
    MAGIC_KEYS(0x0031, "Magic Keys"),
    MAGIC_KEYS_2(0x0032, "Magic Keys"),
    UNKNOWN_0X40(0x0040, "Unknown"),
    SEND_SMART_ROUTING_2_INFO(0x0044, "Send Smart Routing 2.0 Info"),
    FAST_CONNECT_COMPLETE(0x0045, "Fast Connect Complete?"),
    BUD_SWAP_2_PROCEDURE(0x0047, "Bud Swap 2.0 Procedure?"),
    SWAP_IMMINENT_CONFIRM(0x0048, "Swap Imminent Confirm?"),
    BUD_SWAP_2_COMPLETION(0x0049, "Bud Swap 2.0 Completion?"),
    SWAP_COMPLETE_CONFIRM(0x004A, "Swap Complete Confirm?"),
    CONVERSATIONAL_AWARENESS(0x004B, "Conversational Awareness"),
    ADAPTIVE_VOLUME_MESSAGE(0x004C, "Adaptive Volume Message"),

    /**
     * Source Feature Capabilities. Sent by the source after Connect Response to
     * advertise what it supports. In CAPod this is "InitExt" — we only emit a
     * fixed template, we don't read/decode the reply.
     */
    SOURCE_FEATURE_CAPABILITIES(0x004D, "Source Feature Capabilities"),

    FEATURE_PROX_CARD_STATUS_UPDATE(0x004E, "Feature ProxCard Status Update"),
    UARP_DATA(0x004F, "UARP Data"),
    UNKNOWN_0X50(0x0050, "Unknown"),
    SOURCE_CONTEXT(0x0052, "Source Context"),

    /**
     * PME = Personal Medical Equipment (cf. PPE = Personal Protective Equipment) —
     * hearing-aid configuration for the iOS 18.1+ hearing-aid feature on AirPods
     * Pro 2. Decoded as 4 × 8 Float32 values (see [AapSetting.PmeConfig]); see
     * that data class for the layout rationale. "PME Config" is the label the
     * Wireshark AAP dissector uses for this opcode.
     */
    PME_CONFIG(0x0053, "PME Config"),

    /**
     * Configures the AirPods radio's allowed RF bands. AirPods Pro 2 USB-C
     * and newer can transmit on 5 GHz / 6 GHz U-NII bands in addition to
     * 2.4 GHz ISM — Apple uses this for the proprietary lossless audio mode
     * with Apple Vision Pro. The "type" field is a band code from Apple's
     * `BSM_BAND_CODE_*` enum (0x0 = ISM24, 0x1/0x2/0x3 = U-NII-1/3/4,
     * 0x4–0x7 = U-NII-5A/B/C/D, 0x8 = INVALID; identified via FCC firmware
     * analysis). "Set Band Edges" is the label the Wireshark AAP dissector
     * uses for this opcode. CAPod observes but does not decode the payload.
     */
    SET_BAND_EDGES(0x0054, "Set Band Edges"),
    UNKNOWN_0X55(0x0055, "Unknown"),
    USB_SPATIAL_SENSOR_DATA_REQUEST(0x0056, "USB Spatial Sensor Data Request"),
    SLEEP_DETECTION_UPDATE(0x0057, "Sleep Detection Update"),
    UNKNOWN_0X58(0x0058, "Unknown"),
    DYNAMIC_END_OF_CHARGE(0x0059, "Dynamic End Of Charge"),
    PERSONAL_TRANSLATION(0x0060, "Personal Translation"),
    ;

    companion object {
        private val byValue: Map<Int, AapMessageType> = entries.associateBy { it.value }
        fun byValue(value: Int): AapMessageType? = byValue[value]
    }
}
