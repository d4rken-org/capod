package eu.darken.capod.common.debug.logging

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication
import testhelpers.TestTimeSource
import java.io.File
import java.io.IOException

/**
 * A file logger that cannot open its writer used to swallow the failure and wipe the log file on
 * the way out: the recorder was told the recording had started while nothing could be written to
 * it, and a resumed session lost the recording it was continuing.
 *
 * Robolectric because [FileLogger] logs through [android.util.Log] directly, which is not mocked in
 * plain unit tests here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class FileLoggerTest : BaseTest() {

    private val timeSource = TestTimeSource()

    private val sessionDir: File
        get() = File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "filelogger-test")

    @Before
    fun cleanSessionDir() {
        sessionDir.deleteRecursively()
        sessionDir.mkdirs()
    }

    @After
    fun removeSessionDir() {
        sessionDir.deleteRecursively()
    }

    @Test
    fun `a log file that cannot be opened fails the start`() {
        // core.log occupied by a directory: the writer cannot be opened.
        val logFile = File(sessionDir, "core.log").also { it.mkdirs() }

        val logger = FileLogger(logFile, timeSource)

        shouldThrow<IOException> { logger.start() }

        // Inert rather than half-started: nothing was published, so neither writing nor stopping
        // does anything.
        logger.log(Logging.Priority.INFO, "tag", "dropped", null)
        logger.stop()
    }

    @Test
    fun `a failed start leaves the logger startable`() {
        val logFile = File(sessionDir, "core.log").also { it.mkdirs() }
        val logger = FileLogger(logFile, timeSource)
        shouldThrow<IOException> { logger.start() }

        // With the obstruction gone the same logger has to start for real: the failed attempt must
        // not have left a writer reference behind that makes the retry a no-op.
        logFile.deleteRecursively()
        logger.start()
        logger.log(Logging.Priority.INFO, "tag", "recorded", null)
        logger.stop()

        logFile.readText() shouldContain "recorded"
    }

    /**
     * A resumed session appends to the log file of the recording it continues. Cleaning up after a
     * failed open deleted that file unconditionally, so a resume that could not append (a full
     * disk) destroyed the recording the user was about to send.
     */
    @Test
    fun `a failed start keeps a log file it did not create`() {
        val logFile = File(sessionDir, "core.log")
        logFile.writeText("=== BEGIN ===\nprevious recording\n")
        // Read-only: the append cannot be opened, but the file itself is perfectly deletable.
        logFile.setWritable(false, false)
        assumeTrue("The read-only bit is not enforced for this user", !logFile.canWrite())

        try {
            val logger = FileLogger(logFile, timeSource)

            // The failure has to surface: the module's rollback only runs if it does.
            shouldThrow<IOException> { logger.start() }

            // Only a file THIS attempt created may be cleaned up.
            logFile.exists() shouldBe true
            logFile.readText() shouldContain "previous recording"
        } finally {
            logFile.setWritable(true, true)
        }
    }
}
