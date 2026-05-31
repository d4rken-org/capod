package eu.darken.capod.pods.core.apple.aap.protocol

/**
 * Classified Conversational Awareness signal derived from the status byte of a `0x4B` frame.
 *
 * Status-byte mapping (from live AirPods Pro 3 captures + the librepods project):
 * - `1`, `2` → [START] (wearer started / is speaking → engage the reaction)
 * - `5`, `6`, `8`, `9` → [STOP] (wearer stopped → disengage). `5` is the terminal value on fw `…6861`,
 *   which winds down `3`→`5` and never reaches `6/8/9`; `6/8/9` are the terminal values on fw `…6503`.
 * - any other value (`0`, `3`, `4`, `0x0B`, … and anything unrecognised) → [HOLD]: a transitional or
 *   unknown frame. It must NOT disengage the reaction — only an explicit terminal [STOP] does that.
 *
 * Frame cadence is firmware-dependent and the pod does NOT reliably stream keep-alives while you
 * talk: fw `…6861` sent only an onset (`1`,`2`) then NO `0x4B` frames for 21s of continuous speech
 * (proven still-speaking — it held its own CA/ANC-transparency engaged the whole time), then the
 * wind-down `3`,`5`. So frame-silence must NOT be read as "speaking ended"; disengage is driven by
 * the explicit terminal [STOP] frame. [ConversationReaction]'s stale timeout is only a long backstop
 * for a fully-dropped terminal frame, not the normal disengage path.
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
