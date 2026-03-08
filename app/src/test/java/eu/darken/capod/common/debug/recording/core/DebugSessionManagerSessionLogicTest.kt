package eu.darken.capod.common.debug.recording.core

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import testhelpers.BaseTest
import java.io.File

class DebugSessionManagerSessionLogicTest : BaseTest() {

    @TempDir
    lateinit var tempDir: File

    private lateinit var externalLogsDir: File
    private lateinit var cacheLogsDir: File

    @BeforeEach
    fun setup() {
        externalLogsDir = File(tempDir, "external/debug/logs").also { it.mkdirs() }
        cacheLogsDir = File(tempDir, "cache/debug/logs").also { it.mkdirs() }
    }

    private fun logDirs() = listOf(externalLogsDir, cacheLogsDir)

    @Nested
    inner class ParseCreatedAt {
        @Test
        fun `standard format extracts timestamp`() {
            DebugSessionManager.parseCreatedAt("capod_1.2.3_1709810400000_abcd1234", 99L) shouldBe 1709810400000L
        }

        @Test
        fun `zip suffix is stripped before parsing`() {
            DebugSessionManager.parseCreatedAt("capod_1.2.3_1709810400000_abcd1234.zip", 99L) shouldBe 1709810400000L
        }

        @Test
        fun `too few parts returns fallback`() {
            DebugSessionManager.parseCreatedAt("capod_1.2.3", 99L) shouldBe 99L
        }

        @Test
        fun `non-numeric timestamp returns fallback`() {
            DebugSessionManager.parseCreatedAt("capod_1.2.3_notanumber_abcd1234", 99L) shouldBe 99L
        }

        @Test
        fun `timestamp in seconds returns fallback`() {
            DebugSessionManager.parseCreatedAt("capod_1.2.3_1709810400_abcd1234", 99L) shouldBe 99L
        }

        @Test
        fun `empty string returns fallback`() {
            DebugSessionManager.parseCreatedAt("", 42L) shouldBe 42L
        }
    }

    @Nested
    inner class DeriveSessionId {
        @Test
        fun `external dir gets ext prefix`() {
            val file = File("/storage/emulated/0/Android/data/pkg/files/debug/logs/session1")
            DebugSessionManager.deriveSessionId(file) shouldBe "ext:session1"
        }

        @Test
        fun `cache dir gets cache prefix`() {
            val file = File("/data/data/pkg/cache/debug/logs/session1")
            DebugSessionManager.deriveSessionId(file) shouldBe "cache:session1"
        }

        @Test
        fun `zip suffix is stripped`() {
            val file = File("/data/data/pkg/cache/debug/logs/session1.zip")
            DebugSessionManager.deriveSessionId(file) shouldBe "cache:session1"
        }
    }

    @Nested
    inner class ScanSessions {
        @Test
        fun `empty directories returns empty list`() {
            val result = DebugSessionManager.scanSessions(logDirectories = logDirs())
            result.shouldBeEmpty()
        }

        @Test
        fun `dir with valid core log returns Ready`() {
            val sessionDir = File(externalLogsDir, "capod_1.0_1700000000000_abcd1234").also { it.mkdirs() }
            File(sessionDir, "core.log").writeText("some log content")

            val result = DebugSessionManager.scanSessions(logDirectories = logDirs())

            result shouldHaveSize 1
            val session = result.first()
            session.shouldBeInstanceOf<DebugSession.Ready>()
            session.id shouldBe "ext:capod_1.0_1700000000000_abcd1234"
            session.logDir shouldBe sessionDir
            session.zipFile shouldBe null
            session.compressedSize shouldBe 0L
        }

        @Test
        fun `dir with empty core log returns Failed EMPTY_LOG`() {
            val sessionDir = File(externalLogsDir, "capod_1.0_1700000000000_abcd1234").also { it.mkdirs() }
            File(sessionDir, "core.log").createNewFile()

            val result = DebugSessionManager.scanSessions(logDirectories = logDirs())

            result shouldHaveSize 1
            val session = result.first()
            session.shouldBeInstanceOf<DebugSession.Failed>()
            (session as DebugSession.Failed).reason shouldBe DebugSession.Failed.Reason.EMPTY_LOG
        }

        @Test
        fun `dir with no core log returns Failed MISSING_LOG`() {
            File(externalLogsDir, "capod_1.0_1700000000000_abcd1234").mkdirs()

            val result = DebugSessionManager.scanSessions(logDirectories = logDirs())

            result shouldHaveSize 1
            val session = result.first()
            session.shouldBeInstanceOf<DebugSession.Failed>()
            (session as DebugSession.Failed).reason shouldBe DebugSession.Failed.Reason.MISSING_LOG
        }

        @Test
        fun `standalone non-empty zip returns Ready with null logDir`() {
            File(externalLogsDir, "capod_1.0_1700000000000_abcd1234.zip").writeText("zipdata")

            val result = DebugSessionManager.scanSessions(logDirectories = logDirs())

            result shouldHaveSize 1
            val session = result.first()
            session.shouldBeInstanceOf<DebugSession.Ready>()
            (session as DebugSession.Ready).logDir shouldBe null
            session.zipFile shouldBe File(externalLogsDir, "capod_1.0_1700000000000_abcd1234.zip")
            session.compressedSize shouldBe File(externalLogsDir, "capod_1.0_1700000000000_abcd1234.zip").length()
        }

        @Test
        fun `standalone empty zip returns Failed CORRUPT_ZIP`() {
            File(externalLogsDir, "capod_1.0_1700000000000_abcd1234.zip").createNewFile()

            val result = DebugSessionManager.scanSessions(logDirectories = logDirs())

            result shouldHaveSize 1
            val session = result.first()
            session.shouldBeInstanceOf<DebugSession.Failed>()
            (session as DebugSession.Failed).reason shouldBe DebugSession.Failed.Reason.CORRUPT_ZIP
        }

        @Test
        fun `dir plus sibling zip reports combined diskSize`() {
            val sessionDir = File(externalLogsDir, "capod_1.0_1700000000000_abcd1234").also { it.mkdirs() }
            File(sessionDir, "core.log").writeText("log content here")
            File(externalLogsDir, "capod_1.0_1700000000000_abcd1234.zip").writeText("zipdata12345")

            val result = DebugSessionManager.scanSessions(logDirectories = logDirs())

            result shouldHaveSize 1
            val session = result.first() as DebugSession.Ready
            val expectedDirSize = sessionDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            val zipFile = File(externalLogsDir, "capod_1.0_1700000000000_abcd1234.zip")
            val expectedZipSize = zipFile.length()
            session.diskSize shouldBe expectedDirSize + expectedZipSize
            session.zipFile shouldBe zipFile
            session.compressedSize shouldBe expectedZipSize
        }

        @Test
        fun `dir missing core log but valid sibling zip returns Ready with null logDir`() {
            File(externalLogsDir, "capod_1.0_1700000000000_abcd1234").mkdirs()
            File(externalLogsDir, "capod_1.0_1700000000000_abcd1234.zip").writeText("zipdata")

            val result = DebugSessionManager.scanSessions(logDirectories = logDirs())

            result shouldHaveSize 1
            val session = result.first()
            session.shouldBeInstanceOf<DebugSession.Ready>()
            (session as DebugSession.Ready).logDir shouldBe null
            session.zipFile shouldBe File(externalLogsDir, "capod_1.0_1700000000000_abcd1234.zip")
        }

        @Test
        fun `active recording dir returns Recording`() {
            val sessionDir = File(externalLogsDir, "capod_1.0_1700000000000_abcd1234").also { it.mkdirs() }
            File(sessionDir, "core.log").writeText("recording in progress")

            val result = DebugSessionManager.scanSessions(
                logDirectories = logDirs(),
                activeDir = sessionDir,
                recordingStartedAt = 1700000000000L,
            )

            result shouldHaveSize 1
            val session = result.first()
            session.shouldBeInstanceOf<DebugSession.Recording>()
            (session as DebugSession.Recording).startedAt shouldBe 1700000000000L
        }

        @Test
        fun `multiple sessions sorted by createdAt descending then id ascending`() {
            val oldDir = File(externalLogsDir, "capod_1.0_1600000000000_abcd1234").also { it.mkdirs() }
            File(oldDir, "core.log").writeText("old log")

            val newDir = File(externalLogsDir, "capod_1.0_1700000000000_abcd1234").also { it.mkdirs() }
            File(newDir, "core.log").writeText("new log")

            val result = DebugSessionManager.scanSessions(logDirectories = logDirs())

            result shouldHaveSize 2
            result[0].createdAt shouldBe 1700000000000L
            result[1].createdAt shouldBe 1600000000000L
        }
    }
}
