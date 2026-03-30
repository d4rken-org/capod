package eu.darken.capod.pods.core.apple.protocol.aap

import eu.darken.capod.pods.core.PodModel
import kotlin.reflect.KClass

/**
 * Per-model mapping between AAP wire format and domain types.
 * All wire protocol bytes are encapsulated here — sealed classes [AapSetting] and [AapCommand]
 * contain zero protocol knowledge.
 *
 * If Apple changes a setting ID or value encoding for a new model, only the profile changes.
 */
interface AapDeviceProfile {

    /**
     * Decode a single message into a setting update.
     * Returns null if the message is not a recognized setting.
     * Callers merge the returned pair into existing state (incremental update).
     */
    fun decodeSetting(message: AapMessage): Pair<KClass<out AapSetting>, AapSetting>?

    /**
     * Decode a device info message (typically command type 0x001D).
     */
    fun decodeDeviceInfo(message: AapMessage): AapDeviceInfo?

    /**
     * Encode a domain command into wire bytes ready to send.
     * @throws UnsupportedOperationException if the command is not supported by this profile.
     */
    fun encodeCommand(command: AapCommand): ByteArray

    /**
     * Encode the handshake message that initiates the AAP session.
     */
    fun encodeHandshake(): ByteArray

    companion object {
        fun forModel(model: PodModel): AapDeviceProfile = DefaultAapDeviceProfile()
    }
}
