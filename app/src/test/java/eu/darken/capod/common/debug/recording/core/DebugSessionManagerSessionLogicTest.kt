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
import java.time.Instant

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
        fun `returns creation time from file attributes`() {
            val file = File(externalLogsDir, "capod_1.2.3_1709810400000_abcd1234").also { it.mkdirs() }
            val result = DebugSessionManager.parseCreatedAt(file)
            // Should return a valid Instant (either from file attributes or lastModified fallback)
            result.shouldBeInstanceOf<Instant>()
        }

        @Test
        fun `non-existent file returns epoch fallback`() {
            val file = File(externalLogsDir, "nonexistent")
            val result = DebugSessionManager.parseCreatedAt(file)
            // Falls back to lastModified (0 for non-existent) → Instant.ofEpochMilli(0)
            result shouldBe Instant.EPOCH
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
        fun `ignores non-directory non-zip files`() {
            File(externalLogsDir, "capod_1.0_1700000000000_abcd1234").also { it.mkdirs() }
            File(externalLogsDir, "capod_1.0_1700000000000_abcd1234/core.log").writeText("log")
            File(externalLogsDir, "random_notes.txt").writeText("not a session")

            val result = DebugSessionManager.scanSessions(logDirectories = logDirs())

            result shouldHaveSize 1
            result.first().id shouldBe "ext:capod_1.0_1700000000000_abcd1234"
        }

        @Test
        fun `handles missing log directory gracefully`() {
            val missing = File(tempDir, "nonexistent/path")
            val result = DebugSessionManager.scanSessions(logDirectories = listOf(missing))

            result.shouldBeEmpty()
        }

        @Test
        fun `cache directory gets cache prefix`() {
            val sessionDir = File(cacheLogsDir, "capod_1.0_1700000000000_abcd1234").also { it.mkdirs() }
            File(sessionDir, "core.log").writeText("cached log")

            val result = DebugSessionManager.scanSessions(logDirectories = logDirs())

            result shouldHaveSize 1
            result.first().id shouldBe "cache:capod_1.0_1700000000000_abcd1234"
        }

        @Test
        fun `empty core log with valid sibling zip returns Ready`() {
            val sessionDir = File(externalLogsDir, "capod_1.0_1700000000000_abcd1234").also { it.mkdirs() }
            File(sessionDir, "core.log").createNewFile()
            File(externalLogsDir, "capod_1.0_1700000000000_abcd1234.zip").writeText("valid zip data")

            val result = DebugSessionManager.scanSessions(logDirectories = logDirs())

            result shouldHaveSize 1
            val session = result.first()
            session.shouldBeInstanceOf<DebugSession.Ready>()
            (session as DebugSession.Ready).logDir shouldBe null
            session.zipFile shouldBe File(externalLogsDir, "capod_1.0_1700000000000_abcd1234.zip")
        }

        @Test
        fun `multiple log directories are combined`() {
            val extDir = File(externalLogsDir, "capod_1.0_1700000000000_aaaa1111").also { it.mkdirs() }
            File(extDir, "core.log").writeText("ext log")

            val cacheDir = File(cacheLogsDir, "capod_1.0_1600000000000_bbbb2222").also { it.mkdirs() }
            File(cacheDir, "core.log").writeText("cache log")

            val result = DebugSessionManager.scanSessions(logDirectories = logDirs())

            result shouldHaveSize 2
            result.any { it.id == "ext:capod_1.0_1700000000000_aaaa1111" } shouldBe true
            result.any { it.id == "cache:capod_1.0_1600000000000_bbbb2222" } shouldBe true
        }

        @Test
        fun `sessions with same createdAt sorted by id ascending`() {
            val dirA = File(externalLogsDir, "capod_1.0_1700000000000_aaaa").also { it.mkdirs() }
            File(dirA, "core.log").writeText("log a")

            val dirB = File(externalLogsDir, "capod_1.0_1700000000000_zzzz").also { it.mkdirs() }
            File(dirB, "core.log").writeText("log b")

            val result = DebugSessionManager.scanSessions(logDirectories = logDirs())

            result shouldHaveSize 2
            result[0].id shouldBe "ext:capod_1.0_1700000000000_aaaa"
            result[1].id shouldBe "ext:capod_1.0_1700000000000_zzzz"
        }

        @Test
        fun `multiple sessions sorted by createdAt descending then id ascending`() {
            // Create dirs with a time gap so filesystem timestamps differ
            val oldDir = File(externalLogsDir, "capod_1.0_1600000000000_abcd1234").also { it.mkdirs() }
            File(oldDir, "core.log").writeText("old log")
            oldDir.setLastModified(1600000000000L)

            val newDir = File(externalLogsDir, "capod_1.0_1700000000000_abcd1234").also { it.mkdirs() }
            File(newDir, "core.log").writeText("new log")
            newDir.setLastModified(1700000000000L)

            val result = DebugSessionManager.scanSessions(logDirectories = logDirs())

            result shouldHaveSize 2
            // Newer session should come first (descending)
            result[0].createdAt.isAfter(result[1].createdAt) shouldBe true
        }
    }
}
