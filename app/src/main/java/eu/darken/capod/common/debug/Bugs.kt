package eu.darken.capod.common.debug

import eu.darken.capod.common.debug.autoreport.AutomaticBugReporter
import eu.darken.capod.common.debug.logging.Logging.Priority.ERROR
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag

object Bugs {
    var reporter: AutomaticBugReporter? = null
    fun report(
        tag: String,
        message: String,
        exception: Throwable
    ) {
        log(TAG, VERBOSE) { "Reporting $exception" }
        log(tag, ERROR) { "$message\n${exception.asLog()}" }

        reporter?.notify(exception) ?: run {
            log(TAG, WARN) { "Bug tracking not initialized yet." }
        }
    }

    private val TAG = logTag("Bugs")
}