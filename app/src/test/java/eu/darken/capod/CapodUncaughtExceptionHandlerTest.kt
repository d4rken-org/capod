package eu.darken.capod

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class CapodUncaughtExceptionHandlerTest : BaseTest() {

    @Test
    fun `suppresses first main thread foreground service timing exception`() {
        val mainThread = Thread.currentThread()
        val previousHandler = RecordingHandler()
        val reports = mutableListOf<Throwable>()
        var loopCalls = 0
        val handler = CapodUncaughtExceptionHandler(
            previousHandler = previousHandler,
            mainThreadProvider = { mainThread },
            loopMainThread = { loopCalls++ },
            reportForegroundServiceTimingException = { reports += it },
            exit = { throw AssertionError("exitProcess($it)") },
        )
        val throwable = ForegroundServiceDidNotStartInTimeException()

        handler.uncaughtException(mainThread, throwable)

        loopCalls shouldBe 1
        reports shouldBe listOf(throwable)
        previousHandler.throwables shouldBe emptyList()
    }

    @Test
    fun `delegates repeated main thread foreground service timing exception`() {
        val mainThread = Thread.currentThread()
        val previousHandler = RecordingHandler()
        val reports = mutableListOf<Throwable>()
        var loopCalls = 0
        val handler = CapodUncaughtExceptionHandler(
            previousHandler = previousHandler,
            mainThreadProvider = { mainThread },
            loopMainThread = { loopCalls++ },
            reportForegroundServiceTimingException = { reports += it },
            exit = { throw AssertionError("exitProcess($it)") },
        )
        val first = ForegroundServiceDidNotStartInTimeException()
        val second = ForegroundServiceDidNotStartInTimeException()

        handler.uncaughtException(mainThread, first)
        handler.uncaughtException(mainThread, second)

        loopCalls shouldBe 1
        reports shouldBe listOf(first)
        previousHandler.throwables shouldBe listOf(second)
    }

    @Test
    fun `delegates foreground service timing exception from non-main thread`() {
        val mainThread = Thread.currentThread()
        val workerThread = Thread()
        val previousHandler = RecordingHandler()
        val handler = CapodUncaughtExceptionHandler(
            previousHandler = previousHandler,
            mainThreadProvider = { mainThread },
            loopMainThread = { throw AssertionError("loopMainThread should not run") },
            reportForegroundServiceTimingException = { throw AssertionError("report should not run") },
            exit = { throw AssertionError("exitProcess($it)") },
        )
        val throwable = ForegroundServiceDidNotStartInTimeException()

        handler.uncaughtException(workerThread, throwable)

        previousHandler.throwables shouldBe listOf(throwable)
    }

    @Test
    fun `delegates unrelated main thread exception`() {
        val mainThread = Thread.currentThread()
        val previousHandler = RecordingHandler()
        val handler = CapodUncaughtExceptionHandler(
            previousHandler = previousHandler,
            mainThreadProvider = { mainThread },
            loopMainThread = { throw AssertionError("loopMainThread should not run") },
            reportForegroundServiceTimingException = { throw AssertionError("report should not run") },
            exit = { throw AssertionError("exitProcess($it)") },
        )
        val throwable = IllegalStateException("boom")

        handler.uncaughtException(mainThread, throwable)

        previousHandler.throwables shouldBe listOf(throwable)
    }

    @Test
    fun `cancels before delegating fatal exception`() {
        val mainThread = Thread.currentThread()
        val events = mutableListOf<String>()
        val previousHandler = object : Thread.UncaughtExceptionHandler {
            override fun uncaughtException(thread: Thread, throwable: Throwable) {
                events += "delegate"
            }
        }
        val throwable = IllegalStateException("boom")
        val handler = CapodUncaughtExceptionHandler(
            previousHandler = previousHandler,
            mainThreadProvider = { mainThread },
            loopMainThread = { throw AssertionError("loopMainThread should not run") },
            reportForegroundServiceTimingException = { throw AssertionError("report should not run") },
            cancelBeforeDelegate = { events += "cancel" },
            exit = { throw AssertionError("exitProcess($it)") },
        )

        handler.uncaughtException(mainThread, throwable)

        events shouldBe listOf("cancel", "delegate")
    }

    @Test
    fun `delegates loop failure after suppression`() {
        val mainThread = Thread.currentThread()
        val previousHandler = RecordingHandler()
        val loopFailure = IllegalStateException("loop failed")
        val handler = CapodUncaughtExceptionHandler(
            previousHandler = previousHandler,
            mainThreadProvider = { mainThread },
            loopMainThread = { throw loopFailure },
            reportForegroundServiceTimingException = {},
            exit = { throw AssertionError("exitProcess($it)") },
        )

        handler.uncaughtException(mainThread, ForegroundServiceDidNotStartInTimeException())

        previousHandler.throwables shouldBe listOf(loopFailure)
    }

    private class RecordingHandler : Thread.UncaughtExceptionHandler {
        val throwables = mutableListOf<Throwable>()

        override fun uncaughtException(thread: Thread, throwable: Throwable) {
            throwables += throwable
        }
    }

    private class ForegroundServiceDidNotStartInTimeException : RuntimeException("timed out")
}
