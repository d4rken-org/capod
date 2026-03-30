package eu.darken.capod.pods.core.apple.protocol.aap

/**
 * Outbound commands to change device settings. Pure domain — no wire protocol bytes.
 * The [AapDeviceProfile] encodes these into the device-specific wire format.
 */
sealed class AapCommand {
    data class SetAncMode(val mode: AncModeValue) : AapCommand()
    data class SetConversationalAwareness(val enabled: Boolean) : AapCommand()
}
