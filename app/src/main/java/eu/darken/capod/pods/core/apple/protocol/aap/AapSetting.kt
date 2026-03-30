package eu.darken.capod.pods.core.apple.protocol.aap

/**
 * Device-reported settings. Pure domain — no wire protocol bytes.
 * Each subclass represents a capability with its current state and supported values.
 * The [AapDeviceProfile] handles all wire ↔ domain translation.
 */
sealed class AapSetting {

    data class AncMode(
        val current: AncModeValue,
        val supported: List<AncModeValue>,
    ) : AapSetting()

    data class ConversationalAwareness(
        val enabled: Boolean,
    ) : AapSetting()
}
