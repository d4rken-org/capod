package eu.darken.capod.pods.core.apple.aap.protocol

import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.AapPodState
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
     * Decode a battery notification (command 0x04).
     * Returns null if the message is not a battery update.
     */
    fun decodeBattery(message: AapMessage): Map<AapPodState.BatteryType, AapPodState.Battery>?

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

    /**
     * Encode notification enable packets. These tell the device to push
     * battery, settings, and other event notifications.
     * Sent after the handshake, before the read loop starts.
     */
    fun encodeNotificationEnable(): List<ByteArray>

    /**
     * Encode the extended init packet (0x4D).
     * Enables advanced features (Adaptive Transparency, Conversational Awareness during playback)
     * on H2+ devices; silently ignored by older models.
     */
    fun encodeInitExt(): ByteArray

    /**
     * Encode a private key request (command 0x30).
     * Returns null if the model doesn't support key exchange.
     */
    fun encodePrivateKeyRequest(): ByteArray?

    /**
     * Decode a private key response (command 0x31).
     * Returns null if the message is not a key response.
     */
    fun decodePrivateKeyResponse(message: AapMessage): KeyExchangeResult?

    /**
     * Decode a stem press event (command 0x19).
     * Returns null if the message is not a stem press event.
     */
    fun decodeStemPress(message: AapMessage): StemPressEvent?

    companion object {
        fun forModel(model: PodModel): AapDeviceProfile = DefaultAapDeviceProfile(model)
    }
}
