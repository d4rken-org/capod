package eu.darken.capod.pods.core.apple.aap.protocol

/**
 * Catalog of AAP control/setting IDs — the 8-bit ID at payload byte 0 of a
 * Control message (see [AapMessageType.CONTROL], opcode 0x0009).
 *
 * Sources:
 *  - https://github.com/pabloaul/apple-wireshark/blob/main/plugins/aacp.lua
 *  - LibrePods / MagicPodsCore
 *  - Real device captures (AirPods Pro 1, Pro 2 USB-C, Pro 3)
 *
 * Where CAPod ships a setting under a different user-facing name than the
 * Wireshark catalog uses, both names are preserved — the enum uses the
 * Wireshark name, UI-facing strings use the CAPod name.
 */
enum class AapControlId(val value: Int, val wiresharkName: String) {
    MIC_MODE(0x01, "Mic Mode"),
    SCAN(0x02, "Scan"),
    RESET(0x03, "Reset"),
    BASIC_DOUBLE_TAP_MODE(0x04, "Basic Double Tap Mode"),
    BUTTON_SEND_MODE(0x05, "Button Send Mode"),
    OWNERSHIP_STATE(0x06, "Ownership state"),
    TAP_INTERVAL(0x07, "Tap Interval"),

    /** Request the connected bud to go secondary. */
    BUD_ROLE(0x08, "Bud Role"),

    DEBUG_GET_DATA(0x09, "Debug Get Data"),
    IN_EAR_DETECTION(0x0A, "In Ear Detection"),

    /** Aka "Dynamic Latency". */
    JITTER_BUFFER(0x0B, "Jitter Buffer"),

    DOUBLE_TAP_MODE(0x0C, "Double Tap Mode"),
    LISTEN_MODE(0x0D, "Listen Mode"),
    HEART_RATE_MONITOR_1(0x0E, "Heart Rate Monitor"),
    HEART_RATE_MONITOR_2(0x0F, "Heart Rate Monitor"),
    UNKNOWN_0X10(0x10, "Unknown/Unassigned"),
    SWITCH_CONTROL(0x11, "Switch Control"),
    VOICE_TRIGGER(0x12, "Voice Trigger"),

    /** "Dictation over AirPods" for Siri. */
    DOAP_MODE(0x13, "DoAP mode"),

    SINGLE_CLICK(0x14, "Single Click"),
    DOUBLE_CLICK(0x15, "Double Click"),
    CLICK_AND_HOLD(0x16, "Click and Hold"),

    /** CAPod ships this as "Press Speed". */
    DOUBLE_CLICK_INTERVAL(0x17, "Double Click Interval"),

    /** CAPod ships this as "Press Hold Duration". */
    CLICK_AND_HOLD_INTERVAL(0x18, "Click and Hold Interval"),

    UNKNOWN_0X19(0x19, "Unknown/Unassigned"),
    LISTENING_MODE_CONFIGS(0x1A, "Listening Mode Configs"),
    ONE_BUD_ANC_MODE(0x1B, "One Bud ANC Mode"),
    CROWN_ROTATION_DIRECTION(0x1C, "Crown Rotation Direction"),
    UNKNOWN_0X1D(0x1D, "Unknown/Unassigned"),
    AUTO_ANSWER_MODE(0x1E, "Auto Answer Mode"),

    /** CAPod ships this as "Tone Volume". */
    CHIME_VOLUME(0x1F, "Chime Volume"),

    SMART_ROUTING_MODE(0x20, "Smart Routing Mode"),
    UNKNOWN_0X21(0x21, "Unknown/Unassigned"),
    HFP_UPLINK_MODE(0x22, "HFP Uplink Mode"),

    /** CAPod ships this as "Volume Swipe Length". */
    VOLUME_SWIPE_INTERVAL(0x23, "Volume Swipe Interval"),

    /** CAPod ships this as "End Call / Mute Mic". */
    CALL_MANAGEMENT_CONFIG(0x24, "Call Management Config"),

    /** CAPod ships this as "Volume Swipe". */
    VOLUME_SWIPE_MODE(0x25, "Volume Swipe Mode"),

    /**
     * "Adaptive Volume" per Wireshark. CAPod ships it as "Personalized Volume"
     * because that matches the iOS Settings.app label — unclear if this is
     * the same feature or the two sources disagree on naming.
     */
    ADAPTIVE_VOLUME(0x26, "Adaptive Volume"),

    SOFTWARE_MUTE(0x27, "Software Mute"),

    /** CAPod ships this as "Conversational Awareness". */
    CONVERSATION_DETECT(0x28, "Conversation Detect"),

    SELECTIVE_SPEECH_LISTENING(0x29, "Selective Speech Listening"),
    UNKNOWN_0X2A(0x2A, "Unknown/Unassigned"),
    UNKNOWN_0X2B(0x2B, "Unknown/Unassigned"),
    HEARING_AID(0x2C, "Hearing Aid"),
    UNKNOWN_0X2D(0x2D, "Unknown/Unassigned"),

    /** CAPod ships this as "Adaptive Audio Noise". */
    AUTO_ANC_STRENGTH(0x2E, "Auto ANC Strength"),

    HEARING_AID_GAIN_SWIPE(0x2F, "Hearing Aid Gain Swipe"),
    HEART_RATE_MONITOR_3(0x30, "Heart Rate Monitor"),
    IN_CASE_TONE(0x31, "In-Case Tone"),
    SIRI_MULTITONE(0x32, "Siri Multitone"),
    HEARING_ASSIST(0x33, "Hearing Assist"),
    ALLOW_OFF_OPTION(0x34, "Allow Off Option"),
    SLEEP_DETECTION(0x35, "Sleep Detection"),
    ALLOW_AUTO_CONNECT_FROM_AUDIO_ACCESSORY(0x36, "Allow Auto Connect from Audio Accessory"),
    HEARING_PROTECTION_PPE(0x37, "Hearing Protection PPE"),
    PPE_CAP_LEVEL_CONFIG(0x38, "PPE Cap Level Config"),

    /** CAPod ships this as "Stem Config". */
    RAW_GESTURES_CONFIG(0x39, "Raw Gestures Config"),

    ALLOW_TEMPORARY_MANAGED_PAIRING(0x3A, "Allow Temporary Managed Pairing"),
    DYNAMIC_END_OF_CHARGE(0x3B, "Dynamic End of Charge"),
    SYSTEM_SIRI_MODE(0x3C, "System Siri Mode"),

    /** "hearingAidV2SourceRegionSupport" per Wireshark. */
    HEARING_AID_GENERIC(0x3D, "Hearing Aid Generic"),

    UPLINK_EQ_BUD(0x3E, "Uplink EQ Bud"),
    UPLINK_EQ_SOURCE(0x3F, "Uplink EQ Source"),

    /** Separate from [IN_CASE_TONE] (0x31) — this is a volume level, not an on/off. */
    IN_CASE_TONE_VOLUME(0x40, "In Case Tone Volume"),

    DISABLE_BUTTON_INPUT(0x41, "Disable Button Input"),
    EXTENDED_HOLD_AND_RELEASE(0x42, "Extended Hold and Release"),
    ;

    companion object {
        private val byValue: Map<Int, AapControlId> = entries.associateBy { it.value }
        fun byValue(value: Int): AapControlId? = byValue[value]
    }
}
