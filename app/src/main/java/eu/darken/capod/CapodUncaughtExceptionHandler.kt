package eu.darken.capod

import eu.darken.capod.common.debug.logging.Logging.Priority.ERROR
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import kotlin.system.exitProcess

internal class CapodUncaughtExceptionHandler(
    private val previousHandler: Thread.UncaughtExceptionHandler?,
    private val cancelBeforeDelegate: (Throwable) -> Unit = {},
    private val exit: (Int) -> Unit = { exitProcess(it) },
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        runCatching { log(App.TAG, ERROR) { "UNCAUGHT EXCEPTION: ${throwable.asLog()}" } }
        runCatching { cancelBeforeDelegate(throwable) }
        previousHandler?.uncaughtException(thread, throwable) ?: exit(1)
    }
}
