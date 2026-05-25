package eu.darken.capod.pods.core.apple.aap.protocol

/**
 * Classified Conversational Awareness signal derived from the status byte of a `0x4B` frame.
 *
 * Status-byte mapping (confirmed against a live AirPods Pro 3 capture and the librepods project):
 * - `1`, `2` → [START] (wearer started / is speaking → engage the reaction)
 * - `6`, `8`, `9` → [STOP] (wearer stopped → disengage)
 * - any other value (`3`, `4`, `0x0B`, …) → [HOLD] (intermediate "still in session" frame; the pod
 *   streams these while speaking — they act as a keep-alive and must NOT disengage the reaction)
 *
 * The pod emits no `0x4B` frames at all during silence, so [HOLD] frames ceasing is itself a
 * reliable "speaking ended" signal (used as a stale-timeout fallback for a missed [STOP]).
 */
enum class ConversationAwarenessEvent {
    START,
    HOLD,
    STOP,
    ;

    companion object {
        val SPEAKING_STATUSES = setOf(1, 2)
        val STOPPED_STATUSES = setOf(6, 8, 9)

        fun fromStatus(status: Int): ConversationAwarenessEvent = when (status) {
            in SPEAKING_STATUSES -> START
            in STOPPED_STATUSES -> STOP
            else -> HOLD
        }
    }
}
