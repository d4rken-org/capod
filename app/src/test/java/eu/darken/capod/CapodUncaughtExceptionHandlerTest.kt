package eu.darken.capod

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class CapodUncaughtExceptionHandlerTest : BaseTest() {

    @Test
    fun `delegates foreground service timing exception`() {
        val mainThread = Thread.currentThread()
        val previousHandler = RecordingHandler()
        val handler = CapodUncaughtExceptionHandler(
            previousHandler = previousHandler,
            exit = { throw AssertionError("exitProcess($it)") },
        )
        val throwable = ForegroundServiceDidNotStartInTimeException()

        handler.uncaughtException(mainThread, throwable)

        previousHandler.throwables shouldBe listOf(throwable)
    }

    @Test
    fun `delegates unrelated main thread exception`() {
        val mainThread = Thread.currentThread()
        val previousHandler = RecordingHandler()
        val handler = CapodUncaughtExceptionHandler(
            previousHandler = previousHandler,
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
            cancelBeforeDelegate = { events += "cancel" },
            exit = { throw AssertionError("exitProcess($it)") },
        )

        handler.uncaughtException(mainThread, throwable)

        events shouldBe listOf("cancel", "delegate")
    }

    @Test
    fun `delegates even when cancelBeforeDelegate throws`() {
        val mainThread = Thread.currentThread()
        val previousHandler = RecordingHandler()
        val throwable = IllegalStateException("boom")
        val handler = CapodUncaughtExceptionHandler(
            previousHandler = previousHandler,
            cancelBeforeDelegate = { throw IllegalStateException("shutdown failed") },
            exit = { throw AssertionError("exitProcess($it)") },
        )

        handler.uncaughtException(mainThread, throwable)

        previousHandler.throwables shouldBe listOf(throwable)
    }

    private class RecordingHandler : Thread.UncaughtExceptionHandler {
        val throwables = mutableListOf<Throwable>()

        override fun uncaughtException(thread: Thread, throwable: Throwable) {
            throwables += throwable
        }
    }

    private class ForegroundServiceDidNotStartInTimeException : RuntimeException("timed out")
}
