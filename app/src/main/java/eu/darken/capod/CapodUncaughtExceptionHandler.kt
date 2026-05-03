package eu.darken.capod

import android.os.Looper
import eu.darken.capod.common.debug.Bugs
import eu.darken.capod.common.debug.logging.Logging.Priority.ERROR
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

internal class CapodUncaughtExceptionHandler(
    private val previousHandler: Thread.UncaughtExceptionHandler?,
    private val mainThreadProvider: () -> Thread = { Looper.getMainLooper().thread },
    private val loopMainThread: () -> Unit = { Looper.loop() },
    private val reportForegroundServiceTimingException: (Throwable) -> Unit = { throwable ->
        Bugs.report(
            tag = App.TAG,
            message = "Foreground service timing exception suppressed",
            exception = throwable,
        )
    },
    private val cancelBeforeDelegate: (Throwable) -> Unit = {},
    private val exit: (Int) -> Unit = { exitProcess(it) },
) : Thread.UncaughtExceptionHandler {

    private val foregroundExceptionHandled = AtomicBoolean(false)

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        if (shouldSuppress(thread, throwable)) {
            runCatching {
                log(App.TAG, WARN) { "Suppressed foreground service timing exception: ${throwable.asLog()}" }
                reportForegroundServiceTimingException(throwable)
            }

            val loopResult = runCatching { loopMainThread() }
            if (loopResult.isSuccess) return

            val loopFailure = loopResult.exceptionOrNull()!!
            runCatching {
                log(App.TAG, ERROR) {
                    "Main loop failed after foreground service timing exception suppression: ${loopFailure.asLog()}"
                }
            }
            delegate(thread, loopFailure)
            return
        }

        runCatching { log(App.TAG, ERROR) { "UNCAUGHT EXCEPTION: ${throwable.asLog()}" } }
        delegate(thread, throwable)
    }

    private fun shouldSuppress(thread: Thread, throwable: Throwable): Boolean {
        val isMainThread = runCatching { thread === mainThreadProvider() }.getOrDefault(false)
        return throwable.isForegroundServiceTimingException() &&
            isMainThread &&
            foregroundExceptionHandled.compareAndSet(false, true)
    }

    private fun delegate(thread: Thread, throwable: Throwable) {
        runCatching { cancelBeforeDelegate(throwable) }
        previousHandler?.uncaughtException(thread, throwable) ?: exit(1)
    }
}

internal fun Throwable.isForegroundServiceTimingException(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current.javaClass.simpleName == "ForegroundServiceDidNotStartInTimeException") return true
        current = current.cause
    }
    return false
}
