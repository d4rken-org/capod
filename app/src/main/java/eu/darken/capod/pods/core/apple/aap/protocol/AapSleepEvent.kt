package eu.darken.capod.pods.core.apple.aap.protocol

/**
 * Push-only event emitted by newer AirPods firmware when the Sleep Detection
 * feature (setting 0x35) is enabled (message type 0x57 "Sleep Detection Update").
 *
 * Payload schema is not yet documented publicly — this type preserves the raw
 * bytes so future capture-based analysis can fill in structured fields.
 */
data class AapSleepEvent(
    val rawPayload: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AapSleepEvent) return false
        return rawPayload.contentEquals(other.rawPayload)
    }

    override fun hashCode(): Int = rawPayload.contentHashCode()
}
