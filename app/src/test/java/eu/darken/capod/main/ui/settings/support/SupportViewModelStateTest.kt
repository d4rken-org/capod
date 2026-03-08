package eu.darken.capod.main.ui.settings.support

import eu.darken.capod.common.debug.recording.core.DebugSession
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.io.File
import java.time.Instant

class SupportViewModelStateTest : BaseTest() {

    private val testInstant = Instant.ofEpochMilli(1700000000000L)

    private fun readySession(id: String = "ext:s1", diskSize: Long = 100L) = DebugSession.Ready(
        id = id,
        displayName = "s1",
        createdAt = testInstant,
        diskSize = diskSize,
        logDir = File("/tmp/s1"),
        zipFile = null,
        compressedSize = 0L,
    )

    private fun recordingSession(id: String = "ext:rec") = DebugSession.Recording(
        id = id,
        displayName = "rec",
        createdAt = testInstant,
        diskSize = 50L,
        path = File("/tmp/rec"),
        startedAt = 1700000000000L,
    )

    private fun failedSession(id: String = "ext:fail", diskSize: Long = 10L) = DebugSession.Failed(
        id = id,
        displayName = "fail",
        createdAt = testInstant,
        diskSize = diskSize,
        path = File("/tmp/fail"),
        reason = DebugSession.Failed.Reason.EMPTY_LOG,
    )

    private fun compressingSession(id: String = "ext:comp", diskSize: Long = 75L) = DebugSession.Compressing(
        id = id,
        displayName = "comp",
        createdAt = testInstant,
        diskSize = diskSize,
        path = File("/tmp/comp"),
    )

    @Nested
    inner class LogSessionCount {
        @Test
        fun `empty sessions returns 0`() {
            SupportViewModel.State().logSessionCount shouldBe 0
        }

        @Test
        fun `excludes Recording sessions`() {
            val state = SupportViewModel.State(
                sessions = listOf(recordingSession(), readySession()),
            )
            state.logSessionCount shouldBe 1
        }

        @Test
        fun `includes Compressing and Failed sessions`() {
            val state = SupportViewModel.State(
                sessions = listOf(compressingSession(), failedSession(), readySession()),
            )
            state.logSessionCount shouldBe 3
        }

        @Test
        fun `only Recording sessions returns 0`() {
            val state = SupportViewModel.State(
                sessions = listOf(recordingSession()),
            )
            state.logSessionCount shouldBe 0
        }
    }

    @Nested
    inner class LogFolderSize {
        @Test
        fun `empty sessions returns 0`() {
            SupportViewModel.State().logFolderSize shouldBe 0L
        }

        @Test
        fun `sums all session diskSizes`() {
            val state = SupportViewModel.State(
                sessions = listOf(
                    readySession(id = "ext:a", diskSize = 100L),
                    failedSession(id = "ext:b", diskSize = 50L),
                    recordingSession(),
                    compressingSession(id = "ext:c", diskSize = 75L),
                ),
            )
            state.logFolderSize shouldBe 100L + 50L + 50L + 75L
        }
    }

    @Nested
    inner class FailedSessions {
        @Test
        fun `empty sessions returns empty`() {
            SupportViewModel.State().failedSessions.shouldBeEmpty()
        }

        @Test
        fun `no failed sessions returns empty`() {
            val state = SupportViewModel.State(
                sessions = listOf(readySession(), recordingSession()),
            )
            state.failedSessions.shouldBeEmpty()
        }

        @Test
        fun `filters to only Failed instances`() {
            val failed1 = failedSession(id = "ext:f1")
            val failed2 = failedSession(id = "ext:f2")
            val state = SupportViewModel.State(
                sessions = listOf(readySession(), failed1, recordingSession(), failed2),
            )
            state.failedSessions shouldHaveSize 2
        }
    }
}
