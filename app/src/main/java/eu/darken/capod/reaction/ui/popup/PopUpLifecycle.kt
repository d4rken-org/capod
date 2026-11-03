package eu.darken.capod.reaction.ui.popup

import eu.darken.capod.common.debug.logging.Logging.Priority.ERROR
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag

/**
 * What the popup host should animate towards. [token] identifies the transition, the host hands it
 * back through [PopUpLifecycle.onExitFinished] once a hide transition completed.
 */
data class PopUpTarget(
    val visible: Boolean,
    val token: Long,
)

/**
 * Main-thread state machine for the popup window: hidden, shown, exiting.
 *
 * The window is only removed by a completed exit or the fallback timer of the *current* exit, e.g.
 * close (token 1), show, close (token 2): a late completion for token 1 is ignored.
 */
class PopUpLifecycle<T>(private val effects: Effects<T>) {

    interface Effects<T> {
        /** Creates the host for [payload] with fresh, touchable window params and adds it. Throws on failure. */
        fun attach(payload: T, target: PopUpTarget)
        fun update(payload: T)
        fun setTarget(target: PopUpTarget)
        fun setTouchable(touchable: Boolean)
        fun scheduleFallback(token: Long)
        fun cancelFallback()
        fun destroyHost()
        fun removeWindow()
        fun release()
    }

    enum class State { HIDDEN, SHOWN, EXITING }

    var state: State = State.HIDDEN
        private set

    private var generation = 0L
    private var exitToken: Long? = null

    fun show(payload: T) {
        when (state) {
            State.SHOWN -> {
                log(TAG) { "Popup already visible, updating device." }
                effects.update(payload)
            }

            State.EXITING -> {
                log(TAG) { "State: EXITING -> SHOWN (cancelling exit token=$exitToken)" }
                exitToken = null
                state = State.SHOWN
                guarded("cancelFallback") { effects.cancelFallback() }
                guarded("update") { effects.update(payload) }
                guarded("setTouchable") { effects.setTouchable(true) }
                guarded("setTarget") { effects.setTarget(PopUpTarget(visible = true, token = ++generation)) }
            }

            State.HIDDEN -> {
                try {
                    effects.attach(payload, PopUpTarget(visible = true, token = ++generation))
                } catch (e: Exception) {
                    log(TAG, ERROR) { "attach failed: ${e.asLog()}" }
                    teardown()
                    return
                }
                state = State.SHOWN
                log(TAG) { "State: HIDDEN -> SHOWN" }
            }
        }
    }

    fun close() {
        when (state) {
            State.HIDDEN -> log(TAG) { "View was not added" }
            State.EXITING -> log(TAG) { "Already exiting (token=$exitToken)" }
            State.SHOWN -> {
                val token = ++generation
                exitToken = token
                state = State.EXITING
                log(TAG) { "State: SHOWN -> EXITING (token=$token)" }
                guarded("setTouchable") { effects.setTouchable(false) }
                guarded("setTarget") { effects.setTarget(PopUpTarget(visible = false, token = token)) }
                guarded("scheduleFallback") { effects.scheduleFallback(token) }
            }
        }
    }

    fun onExitFinished(token: Long) {
        if (state != State.EXITING || token != exitToken) {
            log(TAG) { "Ignoring stale exit completion (token=$token, current=$exitToken, state=$state)" }
            return
        }
        log(TAG) { "Exit finished (token=$token)" }
        teardown()
    }

    fun onFallback(token: Long) {
        if (state != State.EXITING || token != exitToken) {
            log(TAG) { "Ignoring stale fallback (token=$token, current=$exitToken, state=$state)" }
            return
        }
        log(TAG, WARN) { "Exit didn't finish in time, forcing removal (token=$token)" }
        teardown()
    }

    private fun teardown() {
        log(TAG) { "State: $state -> HIDDEN" }
        state = State.HIDDEN
        exitToken = null
        guarded("cancelFallback") { effects.cancelFallback() }
        guarded("destroyHost") { effects.destroyHost() }
        guarded("removeWindow") { effects.removeWindow() }
        guarded("release") { effects.release() }
    }

    private inline fun guarded(name: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            log(TAG, ERROR) { "$name failed: ${e.asLog()}" }
        }
    }

    companion object {
        private val TAG = logTag("Reaction", "PopUp", "Lifecycle")
    }
}
