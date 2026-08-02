package eu.darken.capod.common.debug.recording.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import eu.darken.capod.common.InstallId
import eu.darken.capod.common.TimeSource
import eu.darken.capod.common.debug.logging.FileLogger
import eu.darken.capod.common.debug.logging.Logging
import eu.darken.capod.common.upgrade.UpgradeDiagnostics
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication
import testhelpers.TestTimeSource
import testhelpers.coroutine.TestDispatcherProvider
import java.io.File
import java.time.Duration
import java.time.Instant

/**
 * The "that recording looks too short" prompt is a duration heuristic, and duration was measured
 * against the wall clock. A clock adjustment mid-recording (NTP sync, the user changing the time)
 * therefore either invented a long recording out of a short one or trapped a long recording in the
 * warning. A live session now measures monotonically; only a session resumed from the trigger file
 * has to fall back to the persisted wall time, because monotonic time does not survive a reboot.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class RecorderModuleDurationTest : BaseTest() {

    private fun buildModule(scope: CoroutineScope, timeSource: TimeSource): RecorderModule {
        val diagnostics = mockk<UpgradeDiagnostics>()
        coEvery { diagnostics.debugInfo() } returns null
        return RecorderModule(
            context = ApplicationProvider.getApplicationContext(),
            appScope = scope,
            dispatcherProvider = TestDispatcherProvider(Dispatchers.IO),
            installId = mockk<InstallId>(relaxed = true),
            timeSource = timeSource,
            upgradeDiagnostics = diagnostics,
        )
    }

    /**
     * Real dispatchers: the module drives its recorder from its own scope, and the fake time source
     * is what makes the duration deterministic instead of the scheduler. The recorder is stopped in
     * a nested finally — a mid-test failure must not leave a live recorder behind, whose globally
     * installed [FileLogger] would then write into every later test.
     */
    private fun withModule(
        timeSource: TimeSource,
        block: suspend (RecorderModule) -> Unit,
    ) {
        val moduleScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val fileLoggersBefore = Logging.loggers.filterIsInstance<FileLogger>()
        var module: RecorderModule? = null
        try {
            try {
                module = buildModule(moduleScope, timeSource)
                runBlocking { block(module) }
            } finally {
                // Stop before cancelling: scope cancellation does NOT uninstall a running
                // recorder's global FileLogger.
                module?.let { runBlocking { it.stopRecorder() } }
            }
        } finally {
            moduleScope.cancel()
            // A leaked logger must fail THIS test, not poison later ones. Remove stragglers after
            // asserting so one failure can't cascade.
            val leaked = Logging.loggers.filterIsInstance<FileLogger>() - fileLoggersBefore.toSet()
            leaked.forEach { Logging.remove(it) }
            leaked shouldBe emptyList<FileLogger>()
        }
    }

    // A session the module can resume from: the real two-line trigger file plus an existing dir.
    private fun seedTriggerFile(startTime: Long): File {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sessionDir = File(context.getExternalFilesDir(null), "debug/logs/capod_resumed_session")
        sessionDir.mkdirs()
        File(context.getExternalFilesDir(null), "capod_force_debug_run")
            .writeText("${sessionDir.absolutePath}\n$startTime")
        return sessionDir
    }

    @Test
    fun `an eight second recording warns`() {
        val timeSource = TestTimeSource(elapsedRealtimeMs = 100_000L)
        withModule(timeSource) { module ->
            module.startRecorder()

            timeSource.advanceBy(Duration.ofSeconds(8))
            module.requestStopRecorder() shouldBe RecorderModule.StopResult.TooShort
            module.state.first().isRecording shouldBe true

            // "Stop anyway" is the user's own next step, and past the threshold it stops cleanly.
            timeSource.advanceBy(Duration.ofSeconds(3))
            module.requestStopRecorder().shouldBeInstanceOf<RecorderModule.StopResult.Stopped>()
            module.state.first().isRecording shouldBe false
        }
    }

    @Test
    fun `a ten second recording stops`() {
        val timeSource = TestTimeSource(elapsedRealtimeMs = 100_000L)
        withModule(timeSource) { module ->
            module.startRecorder()

            timeSource.advanceBy(Duration.ofSeconds(10))

            val result = module.requestStopRecorder()
            result.shouldBeInstanceOf<RecorderModule.StopResult.Stopped>()
            result.logDir.exists() shouldBe true
            result.sessionId.isNotEmpty() shouldBe true
            module.state.first().isRecording shouldBe false
        }
    }

    @Test
    fun `a backward wall-clock jump does not warn on a long recording`() {
        val timeSource = TestTimeSource(elapsedRealtimeMs = 100_000L)
        withModule(timeSource) { module ->
            module.startRecorder()

            // Twelve real seconds of recording, and an NTP sync that moves the wall clock an hour
            // back. Wall-clock measurement would report a negative duration here.
            timeSource.elapsedRealtimeMs += 12_000L
            timeSource.wallNow = timeSource.wallNow.minus(Duration.ofHours(1))

            module.requestStopRecorder().shouldBeInstanceOf<RecorderModule.StopResult.Stopped>()
        }
    }

    @Test
    fun `a forward wall-clock jump does not skip the warning`() {
        val timeSource = TestTimeSource(elapsedRealtimeMs = 100_000L)
        withModule(timeSource) { module ->
            module.startRecorder()

            // Three real seconds of recording, and a clock correction an hour forward. Wall-clock
            // measurement would call this a one-hour recording and skip the prompt.
            timeSource.elapsedRealtimeMs += 3_000L
            timeSource.wallNow = timeSource.wallNow.plus(Duration.ofHours(1))

            module.requestStopRecorder() shouldBe RecorderModule.StopResult.TooShort
            module.state.first().isRecording shouldBe true
        }
    }

    @Test
    fun `a resumed session measures from the persisted start time`() {
        // Resumed after a process death: there is no monotonic base to measure against, so the
        // persisted wall-clock start is all the module has.
        val timeSource = TestTimeSource(elapsedRealtimeMs = 100_000L)
        val startTime = timeSource.currentTimeMillis() - 8_000L
        seedTriggerFile(startTime)

        withModule(timeSource) { module ->
            module.state.first { it.isRecording }

            module.requestStopRecorder() shouldBe RecorderModule.StopResult.TooShort

            timeSource.wallNow = Instant.ofEpochMilli(startTime + 10_000L)
            module.requestStopRecorder().shouldBeInstanceOf<RecorderModule.StopResult.Stopped>()
        }
    }

    @Test
    fun `a resumed session with a future start time fails open`() {
        // The persisted start lies in the future (the wall clock moved backward across the resume).
        // A negative duration must not trap the user in the warning.
        val timeSource = TestTimeSource(elapsedRealtimeMs = 100_000L)
        seedTriggerFile(timeSource.currentTimeMillis() + 60_000L)

        withModule(timeSource) { module ->
            module.state.first { it.isRecording }

            module.requestStopRecorder().shouldBeInstanceOf<RecorderModule.StopResult.Stopped>()
        }
    }
}
