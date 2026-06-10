package eu.darken.capod.pods.core.apple.aap.protocol

/**
 * Classified Conversational Awareness signal derived from the status byte of a `0x4B` frame.
 *
 * Status-byte mapping (from live AirPods Pro 3 + Pro 2 USB-C captures — both models share one
 * firmware train and a byte-identical protocol — plus the librepods project):
 * - `1`, `2` → [START] (wearer started / is speaking → engage the reaction)
 * - `5`, `6`, `8`, `9` → [STOP] (wearer stopped → disengage). All four confirmed live on Pro 2
 *   USB-C fw `…6814`; which one terminates a given flurry varies with how speech ended.
 * - any other value (`0`, `3`, `4`, `7`, `0x0B`, … and anything unrecognised) → [HOLD]: a
 *   transitional wind-down frame (`7` was only discovered on fw `…6814` — the set is open-ended,
 *   so unknown values are deliberately classified as HOLD rather than guessed at).
 *
 * The pod sends NO frames during active speech — it stays engaged (and silent) for as long as it
 * hears nearby voices, 20-30s+ observed. So frame-silence must NOT be read as "speaking ended".
 * Conversely, any non-START frame means the wind-down has begun: a short flurry of transitional
 * and terminal frames (e.g. `3,0xB,4,8,9` or `3,5,7,8,9`). With only ONE pod worn (other in
 * case/disconnected) the terminal is deterministically dropped — the flurry ends on a transitional
 * `4` (#608; reproduced on Pro 3 and Pro 2 alike) — so [ConversationReaction] treats a HOLD as
 * "terminal imminent" and arms a short fuse, with a long stale backstop for a fully-dropped flurry.
 * A flurry's trailing frames may arrive after its terminal; they are ignored once disengaged.
 */
enum class ConversationAwarenessEvent {
    START,
    HOLD,
    STOP,
    ;

    companion object {
        val SPEAKING_STATUSES = setOf(1, 2)
        val STOPPED_STATUSES = setOf(5, 6, 8, 9)

        fun fromStatus(status: Int): ConversationAwarenessEvent = when (status) {
            in SPEAKING_STATUSES -> START
            in STOPPED_STATUSES -> STOP
            else -> HOLD
        }
    }
}
