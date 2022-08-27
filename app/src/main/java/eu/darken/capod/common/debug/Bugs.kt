package eu.darken.capod.common.debug

import com.bugsnag.android.Bugsnag
import eu.darken.capod.common.debug.logging.Logging.Priority.*
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag

object Bugs {
    var ready = false
    fun report(
        tag: String,
        message: String,
        exception: Throwable
    ) {
        log(TAG, VERBOSE) { "Reporting $exception" }
        log(tag, ERROR) { "$message\n${exception.asLog()}" }

        if (!ready) {
            log(TAG, WARN) { "Bug tracking not initialized yet." }
            return
        }
        Bugsnag.notify(exception)
    }

    private val TAG = logTag("Bugs")
}