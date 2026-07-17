package eu.darken.capod.monitor.core

import eu.darken.capod.monitor.core.MonitorKillDetector.ExitRecord
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class MonitorKillDetectorTest : BaseTest() {

    // ApplicationExitInfo constants — inlined here to avoid android.jar in unit tests.
    private val userRequested = 10 // ApplicationExitInfo.REASON_USER_REQUESTED
    private val crash = 4 // ApplicationExitInfo.REASON_CRASH
    private val lowMemory = 3 // ApplicationExitInfo.REASON_LOW_MEMORY
    private val exitSelf = 1 // ApplicationExitInfo.REASON_EXIT_SELF
    private val signaled = 2 // ApplicationExitInfo.REASON_SIGNALED

    private val session = MonitorSessionMark(startedAt = 1000L, pid = 42, versionCode = 7L)

    @Test
    fun `no records - no kill`() {
        MonitorKillDetector.findOsKill(
            records = emptyList(),
            previous = session,
            currentVersionCode = 7L,
        ) shouldBe null
    }

    @Test
    fun `user requested kill of the monitoring process is detected`() {
        MonitorKillDetector.findOsKill(
            records = listOf(ExitRecord(reason = userRequested, timestamp = 2000L, pid = 42)),
            previous = session,
            currentVersionCode = 7L,
        ) shouldBe 2000L
    }

    @Test
    fun `no previous session - no kill regardless of records`() {
        MonitorKillDetector.findOsKill(
            records = listOf(ExitRecord(reason = userRequested, timestamp = 2000L, pid = 42)),
            previous = null,
            currentVersionCode = 7L,
        ) shouldBe null
    }

    @Test
    fun `session newer than record - no kill`() {
        // A restarted session's mark postdates the exit record: the kill was self-recovered.
        MonitorKillDetector.findOsKill(
            records = listOf(ExitRecord(reason = userRequested, timestamp = 2000L, pid = 42)),
            previous = session.copy(startedAt = 3000L),
            currentVersionCode = 7L,
        ) shouldBe null
    }

    @Test
    fun `pid mismatch - no kill`() {
        // A swipe-kill of a later UI-only process must not be blamed on the old monitor session.
        MonitorKillDetector.findOsKill(
            records = listOf(ExitRecord(reason = userRequested, timestamp = 2000L, pid = 99)),
            previous = session,
            currentVersionCode = 7L,
        ) shouldBe null
    }

    @Test
    fun `app update - no kill`() {
        // Before API 34, package updates kill the old process with REASON_USER_REQUESTED too.
        MonitorKillDetector.findOsKill(
            records = listOf(ExitRecord(reason = userRequested, timestamp = 2000L, pid = 42)),
            previous = session,
            currentVersionCode = 8L,
        ) shouldBe null
    }

    @Test
    fun `non user-requested reasons never trigger`() {
        listOf(crash, lowMemory, exitSelf, signaled).forEach { reason ->
            MonitorKillDetector.findOsKill(
                records = listOf(ExitRecord(reason = reason, timestamp = 2000L, pid = 42)),
                previous = session,
                currentVersionCode = 7L,
            ) shouldBe null
        }
    }

    @Test
    fun `multiple qualifying records - newest wins`() {
        MonitorKillDetector.findOsKill(
            records = listOf(
                ExitRecord(reason = userRequested, timestamp = 2000L, pid = 42),
                ExitRecord(reason = userRequested, timestamp = 4000L, pid = 42),
                ExitRecord(reason = crash, timestamp = 5000L, pid = 42),
                ExitRecord(reason = userRequested, timestamp = 3000L, pid = 42),
            ),
            previous = session,
            currentVersionCode = 7L,
        ) shouldBe 4000L
    }

    @Test
    fun `mixed records - only qualifying user-requested ones count`() {
        MonitorKillDetector.findOsKill(
            records = listOf(
                ExitRecord(reason = userRequested, timestamp = 500L, pid = 42), // predates session
                ExitRecord(reason = crash, timestamp = 2500L, pid = 42),
                ExitRecord(reason = userRequested, timestamp = 2200L, pid = 99), // different process
                ExitRecord(reason = userRequested, timestamp = 2000L, pid = 42),
            ),
            previous = session,
            currentVersionCode = 7L,
        ) shouldBe 2000L
    }
}
