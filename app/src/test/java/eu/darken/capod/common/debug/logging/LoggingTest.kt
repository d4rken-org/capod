package eu.darken.capod.common.debug.logging

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class LoggingTest : BaseTest() {

    @Test
    fun `asLog renders normal throwable`() {
        val log = IllegalStateException("boom").asLog()

        log.contains("java.lang.IllegalStateException: boom") shouldBe true
        log.contains("LoggingTest") shouldBe true
    }

    @Test
    fun `asLog falls back when throwable rendering fails`() {
        val log = HostileThrowable().asLog()

        log.contains("HostileThrowable") shouldBe true
        log.contains("stacktrace unavailable") shouldBe true
    }

    @Test
    fun `logInternal ignores logger failures`() {
        Logging.install(ThrowingLogger())

        log("TEST") { "message" }
    }

    private class HostileThrowable : Throwable() {
        override val message: String?
            get() = throw IllegalStateException("message failed")

        override fun toString(): String = throw IllegalStateException("toString failed")
    }

    private class ThrowingLogger : Logging.Logger {
        override fun isLoggable(priority: Logging.Priority): Boolean {
            throw IllegalStateException("isLoggable failed")
        }

        override fun log(
            priority: Logging.Priority,
            tag: String,
            message: String,
            metaData: Map<String, Any>?
        ) {
            throw IllegalStateException("log failed")
        }
    }
}
