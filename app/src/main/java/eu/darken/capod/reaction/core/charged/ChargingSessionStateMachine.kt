package eu.darken.capod.reaction.core.charged

/**
 * Tracks one charging session for one profile and decides when the "charged" notification
 * should be shown or cancelled. Pure Kotlin, no Android/DI/Flow dependencies; instances are
 * driven sequentially from a single coroutine (no internal locking).
 *
 * A session starts when any slot reports charging and collects every slot that charges while
 * the session runs. Per-slot battery highwater marks decide completion: a slot that stops
 * charging at/above the threshold finished naturally (firmware flips the charging flag off at
 * 100%), while a slot that stops below the threshold was genuinely unplugged and resets the
 * session. Stale data suspends the session instead of resetting it — brief BLE gaps while the
 * device sits on the charger must not lose progress.
 *
 * After firing, the notification is taken down again when the user demonstrably reacted to it:
 * battery discharging below the threshold, a new charging session starting, or any activity
 * signal showing the pods are being handled (worn-pod set changed, pod removed from the case,
 * case lid moved). Activity is judged against baselines captured at fire time so pre-existing
 * state doesn't dismiss — only a change does:
 *  - Worn pods and DISCONNECTED slots baseline on the firing frame. They only apply to
 *    sessions containing pod slots — wearing pods says nothing about a case-only charge.
 *  - The lid signal baselines on the UNION of values seen before the fire (this session plus
 *    a few frames leading into it). With one pod worn and one charging, both pods broadcast
 *    and the monitor's dedup alternates between their frames (OPEN from the in-case pod,
 *    NOT_IN_CASE from the worn pod) — a single-frame baseline would read that steady flapping
 *    as activity. Lid movement is a handling signal for any session, including case-only.
 *
 * An activity dismissal latches into [Phase.DISMISSED] so a still-full, still-charging device
 * can't immediately re-fire.
 */
class ChargingSessionStateMachine {

    enum class Slot { LEFT, RIGHT, CASE, HEADSET }

    /** [battery] is a fraction 0..1, matching the monitor layer's battery values. */
    data class SlotData(val battery: Float, val isCharging: Boolean)

    /**
     * Case posture from the BLE lid byte. NOT_IN_CASE isn't a lid position — it means the
     * broadcasting pod is physically outside the case, which is just as much a handling
     * signal as a lid movement (it's the only BLE-side trace of "pod taken out").
     */
    enum class Lid { OPEN, CLOSED, NOT_IN_CASE }

    /** Signals that the user is physically handling the device. All optional/unknown-safe. */
    data class Activity(
        /** Pods currently worn (per-side); null when no ear data at all. */
        val wornSlots: Set<Slot>? = null,
        val lid: Lid? = null,
        /** Slots physically absent from the case (AAP ChargingState.DISCONNECTED). */
        val disconnectedSlots: Set<Slot> = emptySet(),
    )

    sealed interface Input {
        /**
         * Fresh data from a live source (BLE/AAP). [slots] only contains slots with live
         * readings — cached values must never be fed in here. [threshold] is a fraction 0..1.
         */
        data class LiveUpdate(
            val slots: Map<Slot, SlotData>,
            val threshold: Float,
            val activity: Activity = Activity(),
        ) : Input

        /** Device has no live data right now (out of range, lid closed, cache-only). */
        data object StaleUpdate : Input

        /** Toggle disabled, settings changed, or profile deleted. */
        data object Reset : Input
    }

    enum class Output { NONE, SHOW, CANCEL }

    enum class Phase { IDLE, CHARGING, FIRED, DISMISSED, SUSPENDED }

    var phase: Phase = Phase.IDLE
        private set

    /** Phase to resume into when live data returns; only meaningful while SUSPENDED. */
    private var suspendedFrom: Phase = Phase.IDLE

    /** Battery highwater per slot that joined the session. */
    private val highwater = mutableMapOf<Slot, Float>()

    /**
     * Rolling window of the most recent lid observations across all phases. Seeds the at-fire
     * baseline so the OPEN/NOT_IN_CASE alternation (worn pod broadcasts NOT_IN_CASE while the
     * in-case pod broadcasts OPEN; the monitor's dedup serves them in turn) is absorbed — even
     * for an instant fire right after a settings-change reset, since the window is never cleared.
     */
    private val recentLids = ArrayDeque<Lid>()

    // Activity baselines frozen at fire; see class docs.
    private val baselineLids = mutableSetOf<Lid>()
    private var baselineWorn: Set<Slot>? = null
    private var baselineDisconnected: Set<Slot> = emptySet()

    fun process(input: Input): Output {
        if (input is Input.LiveUpdate) input.activity.lid?.let(::recordRecentLid)
        return when (phase) {
            Phase.IDLE -> processIdle(input)
            Phase.CHARGING -> processCharging(input)
            Phase.FIRED -> processFired(input)
            Phase.DISMISSED -> processDismissed(input)
            Phase.SUSPENDED -> processSuspended(input)
        }
    }

    private fun processIdle(input: Input): Output {
        if (input !is Input.LiveUpdate) return Output.NONE
        val charging = input.slots.filterValues { it.isCharging }
        if (charging.isEmpty()) return Output.NONE
        highwater.clear()
        charging.forEach { (slot, data) -> highwater[slot] = data.battery }
        phase = Phase.CHARGING
        return fireIfComplete(input)
    }

    private fun processCharging(input: Input): Output = when (input) {
        is Input.Reset -> reset(cancel = false)
        is Input.StaleUpdate -> suspend(Phase.CHARGING)
        is Input.LiveUpdate -> {
            // Charging slots join the session (e.g. case plugged in later); slots already in
            // it advance their mark; slots absent from this update stay frozen.
            input.slots.forEach { (slot, data) ->
                if (data.isCharging || slot in highwater) {
                    highwater[slot] = maxOf(highwater[slot] ?: 0f, data.battery)
                }
            }
            val unplugged = highwater.any { (slot, mark) ->
                input.slots[slot]?.isCharging == false && mark < input.threshold
            }
            if (unplugged) reset(cancel = false) else fireIfComplete(input)
        }
    }

    private fun processFired(input: Input): Output = when (input) {
        is Input.Reset -> reset(cancel = true)
        is Input.StaleUpdate -> suspend(Phase.FIRED)
        is Input.LiveUpdate -> when {
            // "All slots stopped charging" alone is NOT an unplug signal — firmware flips the
            // charging flag off at 100% while still on power. Slots that never charged this
            // session (e.g. an idle half-empty case) carry no signal either. The session ends
            // on: a new charge context, discharge below threshold, or user activity.
            isNewChargeContext(input) -> {
                // Only CANCEL is reported now; the next update finds IDLE and starts (and, if
                // the data already satisfies the threshold, fires) the new session.
                phase = Phase.IDLE
                highwater.clear()
                Output.CANCEL
            }

            isSessionDischarged(input) -> reset(cancel = true)

            isActivityDetected(input.activity) -> {
                phase = Phase.DISMISSED
                Output.CANCEL
            }

            else -> Output.NONE
        }
    }

    /** Like FIRED, but the notification is already gone — same exits, silent. */
    private fun processDismissed(input: Input): Output = when (input) {
        is Input.Reset -> reset(cancel = false)
        is Input.StaleUpdate -> suspend(Phase.DISMISSED)
        is Input.LiveUpdate -> when {
            isNewChargeContext(input) -> {
                // Nothing to cancel, so unlike FIRED the new session can start right away.
                phase = Phase.IDLE
                highwater.clear()
                processIdle(input)
            }

            isSessionDischarged(input) -> reset(cancel = false)

            else -> Output.NONE
        }
    }

    private fun processSuspended(input: Input): Output = when (input) {
        is Input.Reset -> reset(cancel = suspendedFrom == Phase.FIRED)
        is Input.StaleUpdate -> Output.NONE
        is Input.LiveUpdate -> {
            phase = suspendedFrom
            if (phase == Phase.CHARGING && hasRegressed(input)) {
                // Battery dropped well below a session mark while we were blind — the device
                // was unplugged and used in the meantime. Start over with the fresh data.
                phase = Phase.IDLE
                highwater.clear()
                processIdle(input)
            } else {
                process(input)
            }
        }
    }

    /** Something is charging below the threshold — a fresh charging session is beginning. */
    private fun isNewChargeContext(input: Input.LiveUpdate): Boolean =
        input.slots.any { it.value.isCharging && it.value.battery < input.threshold }

    /** A session slot is off power and drained below the threshold — device is in use. */
    private fun isSessionDischarged(input: Input.LiveUpdate): Boolean = highwater.keys.any { slot ->
        input.slots[slot]?.let { !it.isCharging && it.battery < input.threshold } == true
    }

    private fun isActivityDetected(activity: Activity): Boolean {
        // Worn pods and pod removal only speak about pod slots; a case-only session ignores them.
        if (highwater.keys.any { it != Slot.CASE }) {
            activity.wornSlots?.let { worn ->
                when (val baseline = baselineWorn) {
                    // No ear data at fire time: pods can't be worn while charging in the case,
                    // so any worn pod is activity — only "nothing worn" is adoptable as baseline.
                    null -> if (worn.isNotEmpty()) return true else baselineWorn = emptySet()
                    else -> if (worn != baseline) return true
                }
            }
            val newlyDisconnected = activity.disconnectedSlots.any {
                it != Slot.CASE && it in highwater && it !in baselineDisconnected
            }
            if (newlyDisconnected) return true
        }
        activity.lid?.let { lid ->
            if (baselineLids.isEmpty()) {
                // Lid never observed pre-fire: first sighting is the baseline — it was most
                // likely in that posture all along (open is the normal charging posture).
                baselineLids.add(lid)
            } else if (lid !in baselineLids) {
                return true
            }
        }
        return false
    }

    private fun hasRegressed(input: Input.LiveUpdate): Boolean = highwater.any { (slot, mark) ->
        input.slots[slot]?.let { it.battery < mark - REGRESSION_TOLERANCE } == true
    }

    private fun recordRecentLid(lid: Lid) {
        recentLids.addLast(lid)
        while (recentLids.size > RECENT_LID_FRAMES) recentLids.removeFirst()
    }

    private fun fireIfComplete(input: Input.LiveUpdate): Output {
        if (highwater.isEmpty() || highwater.any { it.value < input.threshold }) return Output.NONE
        phase = Phase.FIRED
        baselineLids.clear()
        baselineLids += recentLids
        baselineWorn = input.activity.wornSlots
        baselineDisconnected = input.activity.disconnectedSlots.intersect(highwater.keys)
        return Output.SHOW
    }

    private fun reset(cancel: Boolean): Output {
        phase = Phase.IDLE
        highwater.clear()
        // recentLids is an ambient rolling window — intentionally kept across resets so an
        // instant re-fire after a settings change still has the recent lid alternation.
        baselineLids.clear()
        baselineWorn = null
        baselineDisconnected = emptySet()
        return if (cancel) Output.CANCEL else Output.NONE
    }

    private fun suspend(from: Phase): Output {
        suspendedFrom = from
        phase = Phase.SUSPENDED
        return Output.NONE
    }

    companion object {
        /**
         * Battery drop (fraction) below a slot's highwater mark that counts as a real
         * regression on resume. Public BLE reports in 10% steps, so a single-step flicker
         * (exactly 0.1) must not reset the session.
         */
        private const val REGRESSION_TOLERANCE = 0.1f

        /**
         * Pre-fire lid observations kept as baseline context. At the observed 0.5-1.5s
         * emission cadence this is a few seconds — enough to capture dedup alternation
         * leading into an instant fire without dragging in long-stale postures.
         */
        private const val RECENT_LID_FRAMES = 8
    }
}
