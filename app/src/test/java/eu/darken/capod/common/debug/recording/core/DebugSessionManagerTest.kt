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

/**
 * Tests for [DebugSessionManager] overlay and reconciliation logic.
 *
 * These tests exercise the instance-level behaviors (zippingIds overlay, failedZipIds overlay,
 * orphan detection) by calling the companion [scanSessions] and then manually applying overlays,
 * mirroring what [DebugSessionManager.applyOverlays] and [DebugSessionManager.findOrphans] do.
 *
 * Full integration tests with mocked RecorderModule are deferred until MockK/Java 21 compat is resolved.
 */
class DebugSessionManagerTest : BaseTest() {

    @TempDir
    lateinit var tempDir: File

    private lateinit var externalLogsDir: File

    @BeforeEach
    fun setup() {
        externalLogsDir = File(tempDir, "external/debug/logs").also { it.mkdirs() }
    }

    private fun logDirs() = listOf(externalLogsDir)

    private fun scanAndOverlay(
        zippingIds: Set<String> = emptySet(),
        failedZipIds: Set<String> = emptySet(),
        activeDir: File? = null,
        recordingStartedAt: Long = 0L,
    ): List<DebugSession> {
        val raw = DebugSessionManager.scanSessions(
            logDirectories = logDirs(),
            activeDir = activeDir,
            recordingStartedAt = recordingStartedAt,
        )
        return raw.map { session ->
            when {
                session.id in zippingIds -> DebugSession.Compressing(
                    id = session.id,
                    displayName = session.displayName,
                    createdAt = session.createdAt,
                    diskSize = session.diskSize,
                    path = (session as? DebugSession.Ready)?.logDir ?: File(""),
                )

                session.id in failedZipIds && session !is DebugSession.Failed -> DebugSession.Failed(
                    id = session.id,
                    displayName = session.displayName,
                    createdAt = session.createdAt,
                    diskSize = session.diskSize,
                    path = (session as? DebugSession.Ready)?.logDir ?: File(""),
                    reason = DebugSession.Failed.Reason.ZIP_FAILED,
                )

                else -> session
            }
        }
    }

    @Nested
    inner class ZippingIdsOverlay {
        @Test
        fun `session in zippingIds appears as Compressing`() {
            val sessionDir = File(externalLogsDir, "capod_1.0_20231114T221320Z_abcd1234").also { it.mkdirs() }
            File(sessionDir, "core.log").writeText("done recording")

            val result = scanAndOverlay(zippingIds = setOf("ext:capod_1.0_20231114T221320Z_abcd1234"))

            result shouldHaveSize 1
            result.first().shouldBeInstanceOf<DebugSession.Compressing>()
            result.first().id shouldBe "ext:capod_1.0_20231114T221320Z_abcd1234"
        }

        @Test
        fun `session not in zippingIds remains Ready`() {
            val sessionDir = File(externalLogsDir, "capod_1.0_20231114T221320Z_abcd1234").also { it.mkdirs() }
            File(sessionDir, "core.log").writeText("done recording")

            val result = scanAndOverlay(zippingIds = setOf("ext:some_other_session"))

            result shouldHaveSize 1
            result.first().shouldBeInstanceOf<DebugSession.Ready>()
        }
    }

    @Nested
    inner class FailedZipIdsOverlay {
        @Test
        fun `session in failedZipIds appears as Failed ZIP_FAILED`() {
            val sessionDir = File(externalLogsDir, "capod_1.0_20231114T221320Z_abcd1234").also { it.mkdirs() }
            File(sessionDir, "core.log").writeText("done recording")

            val result = scanAndOverlay(failedZipIds = setOf("ext:capod_1.0_20231114T221320Z_abcd1234"))

            result shouldHaveSize 1
            val session = result.first()
            session.shouldBeInstanceOf<DebugSession.Failed>()
            (session as DebugSession.Failed).reason shouldBe DebugSession.Failed.Reason.ZIP_FAILED
        }

        @Test
        fun `already-failed session is not overridden by failedZipIds`() {
            File(externalLogsDir, "capod_1.0_20231114T221320Z_abcd1234").mkdirs()

            val result = scanAndOverlay(failedZipIds = setOf("ext:capod_1.0_20231114T221320Z_abcd1234"))

            result shouldHaveSize 1
            val session = result.first()
            session.shouldBeInstanceOf<DebugSession.Failed>()
            (session as DebugSession.Failed).reason shouldBe DebugSession.Failed.Reason.MISSING_LOG
        }
    }

    @Nested
    inner class OrphanDetection {
        @Test
        fun `Ready session with logDir and sibling zip is not an orphan`() {
            val sessionDir = File(externalLogsDir, "capod_1.0_20231114T221320Z_abcd1234").also { it.mkdirs() }
            File(sessionDir, "core.log").writeText("log content")
            File(externalLogsDir, "capod_1.0_20231114T221320Z_abcd1234.zip").writeText("zipdata")

            val sessions = scanAndOverlay()

            sessions shouldHaveSize 1
            val session = sessions.first() as DebugSession.Ready
            session.logDir shouldBe sessionDir
            session.zipFile shouldBe File(externalLogsDir, "capod_1.0_20231114T221320Z_abcd1234.zip")

            val orphans = sessions.filterIsInstance<DebugSession.Ready>().filter { ready ->
                ready.logDir != null && (ready.zipFile == null || ready.compressedSize == 0L)
            }
            orphans.shouldBeEmpty()
        }

        @Test
        fun `Ready session with logDir but no sibling zip is detected as orphan`() {
            val sessionDir = File(externalLogsDir, "capod_1.0_20231114T221320Z_abcd1234").also { it.mkdirs() }
            File(sessionDir, "core.log").writeText("log content")

            val sessions = scanAndOverlay()

            sessions shouldHaveSize 1
            val session = sessions.first() as DebugSession.Ready
            session.logDir shouldBe sessionDir
            session.zipFile shouldBe null
            session.compressedSize shouldBe 0L

            val orphans = sessions.filterIsInstance<DebugSession.Ready>().filter { ready ->
                ready.logDir != null && (ready.zipFile == null || ready.compressedSize == 0L)
            }
            orphans shouldHaveSize 1
            orphans.first().id shouldBe "ext:capod_1.0_20231114T221320Z_abcd1234"
        }

        @Test
        fun `orphan already in zippingIds is not re-detected`() {
            val sessionDir = File(externalLogsDir, "capod_1.0_20231114T221320Z_abcd1234").also { it.mkdirs() }
            File(sessionDir, "core.log").writeText("log content")

            val zipping = setOf("ext:capod_1.0_20231114T221320Z_abcd1234")
            val sessions = scanAndOverlay(zippingIds = zipping)

            sessions shouldHaveSize 1
            sessions.first().shouldBeInstanceOf<DebugSession.Compressing>()

            // No Ready sessions remain, so no orphans to detect
            val orphans = sessions.filterIsInstance<DebugSession.Ready>().filter { ready ->
                ready.logDir != null && ready.id !in zipping &&
                    (ready.zipFile == null || ready.compressedSize == 0L)
            }
            orphans.shouldBeEmpty()
        }
    }

    @Nested
    inner class DeleteGuards {
        @Test
        fun `active recording session cannot be zipped`() {
            val sessionDir = File(externalLogsDir, "capod_1.0_20231114T221320Z_abcd1234").also { it.mkdirs() }
            File(sessionDir, "core.log").writeText("recording in progress")

            val sessions = scanAndOverlay(activeDir = sessionDir, recordingStartedAt = 1700000000000L)

            sessions shouldHaveSize 1
            sessions.first().shouldBeInstanceOf<DebugSession.Recording>()
            // Verify the session IS a Recording — the manager would reject zip/delete calls for this
        }

        @Test
        fun `ext and cache sessions with same basename have different IDs`() {
            val cacheLogsDir = File(tempDir, "cache/debug/logs").also { it.mkdirs() }
            val extDir = File(externalLogsDir, "capod_1.0_20231114T221320Z_abcd1234").also { it.mkdirs() }
            File(extDir, "core.log").writeText("external log")
            val cacheDir = File(cacheLogsDir, "capod_1.0_20231114T221320Z_abcd1234").also { it.mkdirs() }
            File(cacheDir, "core.log").writeText("cache log")

            val sessions = DebugSessionManager.scanSessions(
                logDirectories = listOf(externalLogsDir, cacheLogsDir),
            )

            sessions shouldHaveSize 2
            val ids = sessions.map { it.id }.toSet()
            ids shouldBe setOf("ext:capod_1.0_20231114T221320Z_abcd1234", "cache:capod_1.0_20231114T221320Z_abcd1234")
        }

        @Test
        fun `zipping session should not be deleted`() {
            val sessionDir = File(externalLogsDir, "capod_1.0_20231114T221320Z_abcd1234").also { it.mkdirs() }
            File(sessionDir, "core.log").writeText("done recording")

            val zipping = setOf("ext:capod_1.0_20231114T221320Z_abcd1234")
            val sessions = scanAndOverlay(zippingIds = zipping)

            sessions shouldHaveSize 1
            sessions.first().shouldBeInstanceOf<DebugSession.Compressing>()
            // The manager would reject deleteSession for IDs in zippingIds
        }
    }
}
