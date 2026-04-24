package eu.darken.capod.pods.core.apple.aap.protocol

/**
 * Push-only event paired with the Dynamic End-of-Charge setting (control ID 0x3B).
 * Message type 0x59 — reports charge-cap status transitions (e.g. "80% cap reached").
 *
 * Payload schema is not yet documented publicly — this type preserves the raw
 * bytes so future capture-based analysis can fill in structured fields.
 */
data class AapDynamicEndOfChargeEvent(
    val rawPayload: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AapDynamicEndOfChargeEvent) return false
        return rawPayload.contentEquals(other.rawPayload)
    }

    override fun hashCode(): Int = rawPayload.contentHashCode()
}
