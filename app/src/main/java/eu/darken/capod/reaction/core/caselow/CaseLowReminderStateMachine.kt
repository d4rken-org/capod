package eu.darken.capod.reaction.core.caselow

/**
 * Decides when the low case battery reminder is shown or cancelled for one profile. Pure Kotlin,
 * no Android/DI/Flow dependencies; instances are driven sequentially from a single coroutine
 * (no internal locking).
 *
 * The reminder fires on the transition "pods out of the case" to "both pods back in the case":
 *  - An advert with the pods not both in the case arms the tracker.
 *  - While armed, the first both-in-case advert that carries case battery and charging state
 *    decides: low (at or below the threshold) and not charging shows the reminder; charging or
 *    above the threshold doesn't. Either way the tracker disarms. A both-in-case advert lacking
 *    either value keeps it armed for the next advert.
 *  - A both-in-case advert while unarmed does nothing: the monitor started with the pods already
 *    in the case, or the user is opening the case to take them out.
 *
 * Independent of placement and arming, a charging or above-threshold case cancels the reminder.
 * The cancel is emitted once until the next show. A fresh tracker owes one cancel, which clears a
 * reminder left over from a previous monitor run.
 */
class CaseLowReminderStateMachine {

    sealed interface Input {
        /** [thresholdPercent] is a whole percent; the reminder fires at or below it. */
        data class Update(
            val observation: CaseLowObservation,
            val thresholdPercent: Int,
        ) : Input

        /** Device absent, ambiguous, or without a dual-pod advert. Changes nothing. */
        data object NoObservation : Input

        /** Toggle disabled, settings changed, or profile deleted. */
        data object Reset : Input
    }

    sealed interface Output {
        data object None : Output
        data class Show(val casePercent: Int) : Output
        data object Cancel : Output
    }

    var isArmed: Boolean = false
        private set

    /** Whether the next cancel evidence emits [Output.Cancel]. */
    private var cancelOwed = true

    /** A reminder was shown and not cancelled since. */
    private var isShowing = false

    fun process(input: Input): Output = when (input) {
        is Input.Update -> processUpdate(input.observation, input.thresholdPercent)
        is Input.NoObservation -> Output.None
        is Input.Reset -> reset()
    }

    private fun processUpdate(observation: CaseLowObservation, threshold: Int): Output {
        val percent = observation.casePercent
        val charging = observation.caseCharging

        var output: Output = Output.None

        val cancelEvidence = charging == true || (percent != null && percent > threshold)
        if (cancelEvidence && cancelOwed) {
            cancelOwed = false
            isShowing = false
            output = Output.Cancel
        }

        when {
            !observation.bothPodsInCase -> isArmed = true

            !isArmed -> Unit

            percent == null || charging == null -> Unit

            charging || percent > threshold -> isArmed = false

            else -> {
                isArmed = false
                cancelOwed = true
                isShowing = true
                output = Output.Show(percent)
            }
        }

        return output
    }

    private fun reset(): Output {
        val output = if (isShowing) Output.Cancel else Output.None
        isArmed = false
        cancelOwed = true
        isShowing = false
        return output
    }
}
