package eu.darken.capod.common.debug

import eu.darken.capod.common.debug.autoreport.AutomaticBugReporter
import eu.darken.capod.common.debug.logging.Logging.Priority.ERROR
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.asLogSummary
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import kotlinx.coroutines.flow.MutableStateFlow

object Bugs {
    var reporter: AutomaticBugReporter? = null

    /**
     * True while the debug log recorder is actively recording. Surfaces extra
     * diagnostic UI (e.g. raw BLE payload hex on device cards) so users helping
     * to add support for new headphones can capture useful data without a
     * permanent debug toggle.
     */
    val isDebug = MutableStateFlow(false)

    fun report(
        tag: String,
        message: String,
        exception: Throwable
    ) {
        runCatching { log(TAG, VERBOSE) { "Reporting ${exception.asLogSummary()}" } }
        runCatching { log(tag, ERROR) { "$message\n${exception.asLog()}" } }

        val bugReporter = reporter
        if (bugReporter == null) {
            runCatching { log(TAG, WARN) { "Bug tracking not initialized yet." } }
            return
        }

        runCatching { bugReporter.notify(exception) }
            .onFailure { failure ->
                runCatching { log(TAG, WARN) { "Bug reporter failed: ${failure.asLog()}" } }
            }
    }

    private val TAG = logTag("Bugs")
}
