package eu.darken.capod.pods.core.apple.aap.protocol

/**
 * Classified Conversational Awareness signal derived from the status byte of a `0x4B` frame.
 *
 * Status-byte mapping, derived from a labelled capture set across AirPods Pro 3 and Pro 2 USB-C
 * (`protocol-research/conversationalawareness/` — both models share one firmware train and emit
 * byte-identical sequences). Each scenario was captured with a known action (normal talking,
 * bursty talking, single-pod, volume-up abort, pod removal/case):
 * - `1`, `2` → [START] (conversation onset / wind-up → engage the reaction). A conversation always
 *   opens `1` then `2`; re-onset only ever happens after a terminal.
 * - `5` → [RESUME] (speech resumed after a pause; the wind-down was aborted, CA stays engaged). In
 *   bursty speech the pod cycles `3,5,3,5,…`; `5` is NEVER a terminal. Misreading `5` as a stop was
 *   the root cause of the premature-resume bug — see [ConversationReaction].
 * - `8`, `9` → [STOP] (conversation ended → disengage). The terminal is always the `8`→`9` pair.
 *   Pod removal / case-close also emit `8`,`9` (sometimes with no prior `1`,`2`).
 * - any other value (`3` pause, `4` / `0x0B` wind-down, `7` abort, `6`, and anything unrecognised)
 *   → [HOLD]: a transitional "possible/real wind-down" frame. The real wind-down runs `3→0x0B→4`
 *   then the `8,9` terminal; `7` precedes an aborted terminal. Unknown values are deliberately
 *   classified as HOLD (arm the safety fuse, never resume immediately) rather than guessed at.
 *
 * The pod sends NO frames during active speech — it stays engaged (and silent) for as long as it
 * hears nearby voices, 20-30s+ observed. So frame-silence must NOT be read as "speaking ended".
 * A HOLD means the wind-down may have begun; with only ONE pod worn the `8,9` terminal is
 * deterministically dropped — the flurry ends on `4` (#608, reproduced on Pro 3 and Pro 2) — so
 * [ConversationReaction] treats a HOLD as "terminal imminent" and arms a short fuse, with a long
 * stale backstop for a fully-dropped flurry. A RESUME (`5`) cancels that fuse and re-arms the long
 * backstop; trailing frames after a terminal are ignored once disengaged.
 */
enum class ConversationAwarenessEvent {
    START,
    RESUME,
    HOLD,
    STOP,
    ;

    companion object {
        val SPEAKING_STATUSES = setOf(1, 2)
        val RESUME_STATUSES = setOf(5)
        val STOPPED_STATUSES = setOf(8, 9)

        fun fromStatus(status: Int): ConversationAwarenessEvent = when (status) {
            in SPEAKING_STATUSES -> START
            in RESUME_STATUSES -> RESUME
            in STOPPED_STATUSES -> STOP
            else -> HOLD
        }
    }
}
