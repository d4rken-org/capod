package eu.darken.cap.bugreporting

import com.bugsnag.android.Bugsnag
import eu.darken.cap.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.cap.common.debug.logging.Logging.Priority.WARN
import eu.darken.cap.common.debug.logging.log
import eu.darken.cap.common.debug.logging.logTag

object Bugs {
    var ready = false
    fun report(exception: Exception) {
        log(TAG, VERBOSE) { "Reporting $exception" }
        if (!ready) {
            log(TAG, WARN) { "Bug tracking not initialized yet." }
            return
        }
        Bugsnag.notify(exception)
    }

    private val TAG = logTag("Bugs")
}