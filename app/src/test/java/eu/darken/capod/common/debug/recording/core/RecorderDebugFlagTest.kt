package eu.darken.capod.common.debug.recording.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import eu.darken.capod.common.debug.Bugs
import eu.darken.capod.common.debug.logging.FileLogger
import eu.darken.capod.common.debug.logging.Logging
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.runBlocking
import org.junit.After
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
 * [Recorder] is the writer of [Bugs.isDebug] that sits next to the file logger it installs, so it
 * is the only one that can flip the flag in the same breath as the recording it describes. Asserted
 * against the recorder itself and nothing else: a test that goes through [RecorderModule] is
 * satisfied by the module's own flag collector, and would keep passing if the recorder stopped
 * writing the flag entirely.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class RecorderDebugFlagTest : BaseTest() {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val logDir: File
        get() = File(context.cacheDir, "recorder-flag-test")

    private lateinit var recorder: Recorder

    @Before
    fun setup() {
        logDir.deleteRecursively()
        logDir.mkdirs()
        recorder = Recorder(TestTimeSource())
    }

    @After
    fun teardown() {
        unmockkObject(Logging)
        runBlocking { runCatching { recorder.stop() } }
        Logging.loggers.filterIsInstance<FileLogger>().forEach { Logging.remove(it) }
        logDir.deleteRecursively()
        Bugs.isDebug.value = false
    }

    @Test
    fun `start publishes the debug flag before it returns`() = runBlocking {
        Bugs.isDebug.value shouldBe false

        recorder.start(File(logDir, "core.log"))

        withClue("a recording is live the moment start() returns, so the flag has to read true") {
            Bugs.isDebug.value shouldBe true
        }
    }

    @Test
    fun `stop clears the debug flag`() = runBlocking {
        recorder.start(File(logDir, "core.log"))
        withClue("the flag has to be set before this test can say anything about clearing it") {
            Bugs.isDebug.value shouldBe true
        }

        recorder.stop()

        withClue("no recording is live any more, so the flag has to read false") {
            Bugs.isDebug.value shouldBe false
        }
    }

    @Test
    fun `stop clears the debug flag even when the teardown fails`() = runBlocking {
        recorder.start(File(logDir, "core.log"))
        withClue("the flag has to be set before this test can say anything about clearing it") {
            Bugs.isDebug.value shouldBe true
        }

        // Uninstalling the logger is the first thing stop() does and the only part of it that can
        // throw: FileLogger.stop() swallows its own IO failures.
        mockkObject(Logging)
        every { Logging.remove(any()) } throws IOException("Uninstall failed")

        shouldThrow<IOException> { recorder.stop() }

        withClue("the recorder is no longer recording, failed teardown or not") {
            Bugs.isDebug.value shouldBe false
        }
    }
}
