package eu.darken.capod.common.debug.recording.core

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import testhelpers.BaseTest
import java.io.File

class RecorderModuleTriggerFileTest : BaseTest() {

    @TempDir
    lateinit var tempDir: File

    private val now = 1_700_000_000_000L

    @AfterEach
    fun cleanup() {
        tempDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    @Nested
    inner class ParseTriggerContent {

        @Test
        fun `valid content returns dir and timestamp`() {
            val sessionDir = File(tempDir, "capod_session").also { it.mkdirs() }
            val content = "${sessionDir.absolutePath}\n$now"

            val result = RecorderModule.parseTriggerContent(content, now = now)

            result shouldBe (sessionDir to now)
        }

        @Test
        fun `empty content returns null`() {
            RecorderModule.parseTriggerContent("", now = now).shouldBeNull()
        }

        @Test
        fun `blank content returns null`() {
            RecorderModule.parseTriggerContent("   \n  ", now = now).shouldBeNull()
        }

        @Test
        fun `single line returns null`() {
            RecorderModule.parseTriggerContent("/some/path", now = now).shouldBeNull()
        }

        @Test
        fun `non-numeric timestamp returns null`() {
            val sessionDir = File(tempDir, "capod_session").also { it.mkdirs() }
            val content = "${sessionDir.absolutePath}\nnotanumber"

            RecorderModule.parseTriggerContent(content, now = now).shouldBeNull()
        }

        @Test
        fun `zero timestamp returns null`() {
            val sessionDir = File(tempDir, "capod_session").also { it.mkdirs() }
            val content = "${sessionDir.absolutePath}\n0"

            RecorderModule.parseTriggerContent(content, now = now).shouldBeNull()
        }

        @Test
        fun `negative timestamp returns null`() {
            val sessionDir = File(tempDir, "capod_session").also { it.mkdirs() }
            val content = "${sessionDir.absolutePath}\n-1000"

            RecorderModule.parseTriggerContent(content, now = now).shouldBeNull()
        }

        @Test
        fun `future timestamp beyond skew returns null`() {
            val sessionDir = File(tempDir, "capod_session").also { it.mkdirs() }
            val futureTs = now + 120_000L
            val content = "${sessionDir.absolutePath}\n$futureTs"

            RecorderModule.parseTriggerContent(content, now = now).shouldBeNull()
        }

        @Test
        fun `future timestamp within skew is accepted`() {
            val sessionDir = File(tempDir, "capod_session").also { it.mkdirs() }
            val nearFutureTs = now + 30_000L
            val content = "${sessionDir.absolutePath}\n$nearFutureTs"

            val result = RecorderModule.parseTriggerContent(content, now = now)

            result shouldBe (sessionDir to nearFutureTs)
        }

        @Test
        fun `non-existent directory returns null`() {
            val content = "/nonexistent/path/capod_session\n$now"

            RecorderModule.parseTriggerContent(content, now = now).shouldBeNull()
        }

        @Test
        fun `content with trailing whitespace is handled`() {
            val sessionDir = File(tempDir, "capod_session").also { it.mkdirs() }
            val content = "  ${sessionDir.absolutePath}\n$now  \n"

            val result = RecorderModule.parseTriggerContent(content, now = now)

            result shouldBe (sessionDir to now)
        }

        @Test
        fun `old timestamp is accepted`() {
            val sessionDir = File(tempDir, "capod_session").also { it.mkdirs() }
            val oldTs = 1_000L
            val content = "${sessionDir.absolutePath}\n$oldTs"

            val result = RecorderModule.parseTriggerContent(content, now = now)

            result shouldBe (sessionDir to oldTs)
        }

        @Test
        fun `extra lines are ignored`() {
            val sessionDir = File(tempDir, "capod_session").also { it.mkdirs() }
            val content = "${sessionDir.absolutePath}\n$now\nextra\ndata"

            val result = RecorderModule.parseTriggerContent(content, now = now)

            result shouldBe (sessionDir to now)
        }
    }
}
