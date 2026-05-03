package eu.darken.capod.common.debug

import android.app.Application
import eu.darken.capod.common.debug.autoreport.AutomaticBugReporter
import eu.darken.capod.common.debug.logging.Logging
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class BugsTest : BaseTest() {

    @AfterEach
    fun cleanup() {
        Bugs.reporter = null
        Logging.clearAll()
    }

    @Test
    fun `report does not throw if logging fails`() {
        Logging.clearAll()
        Logging.install(ThrowingLogger())

        shouldNotThrowAny {
            Bugs.report(TAG, "Something failed", HostileThrowable())
        }
    }

    @Test
    fun `report does not throw if reporter fails`() {
        var notified = false
        Bugs.reporter = object : AutomaticBugReporter {
            override fun setup(application: Application) = Unit

            override fun notify(throwable: Throwable) {
                notified = true
                throw IllegalStateException("reporter failed")
            }
        }

        shouldNotThrowAny {
            Bugs.report(TAG, "Something failed", IllegalStateException("boom"))
        }
        notified shouldBe true
    }

    private class HostileThrowable : Throwable() {
        override val message: String?
            get() = throw IllegalStateException("message failed")

        override fun toString(): String = throw IllegalStateException("toString failed")
    }

    private class ThrowingLogger : Logging.Logger {
        override fun isLoggable(priority: Logging.Priority): Boolean = true

        override fun log(
            priority: Logging.Priority,
            tag: String,
            message: String,
            metaData: Map<String, Any>?
        ) {
            throw IllegalStateException("log failed")
        }
    }

    companion object {
        private const val TAG = "TEST"
    }
}
