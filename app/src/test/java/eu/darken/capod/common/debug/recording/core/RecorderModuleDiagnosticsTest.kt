package eu.darken.capod.common.debug.recording.core

import androidx.test.core.app.ApplicationProvider
import eu.darken.capod.common.BuildConfigWrap
import eu.darken.capod.common.InstallId
import eu.darken.capod.common.SystemTimeSource
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.logging.FileLogger
import eu.darken.capod.common.debug.logging.Logging
import eu.darken.capod.common.upgrade.UpgradeDiagnostics
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication
import testhelpers.coroutine.TestDispatcherProvider
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.system.measureTimeMillis

/**
 * The recording header reads diagnostics that live outside the recorder. Those reads happen AFTER
 * the recorder is already writing, so a guarded failure must never abort the state update — that
 * would leave a running recorder the module no longer knows about, i.e. a debug recording that
 * can't be stopped or collected. Failures that DO escape the header have to take the uncommitted
 * recorder down with them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class RecorderModuleDiagnosticsTest : BaseTest() {

    private val logLines = CopyOnWriteArrayList<String>()
    private val logCapture = object : Logging.Logger {
        override fun log(priority: Logging.Priority, tag: String, message: String, metaData: Map<String, Any>?) {
            logLines.add(message)
        }
    }

    @Before
    fun installLogCapture() {
        Logging.install(logCapture)
    }

    @After
    fun removeLogCapture() {
        Logging.remove(logCapture)
    }

    private fun buildModule(
        scope: CoroutineScope,
        upgradeDiagnostics: UpgradeDiagnostics,
        dispatcherProvider: DispatcherProvider = TestDispatcherProvider(),
    ) = RecorderModule(
        context = ApplicationProvider.getApplicationContext(),
        appScope = scope,
        dispatcherProvider = dispatcherProvider,
        installId = mockk<InstallId>(relaxed = true),
        timeSource = SystemTimeSource,
        upgradeDiagnostics = upgradeDiagnostics,
    )

    /**
     * Real dispatchers on purpose: the header's read deadline is wall-clock, so a virtual-time test
     * would skip past it instead of exercising it — an ignored seam has to fail this, not pass after
     * the full production budget. The seam is set before [RecorderModule.startRecorder] so no header
     * read can run against the production bound.
     */
    private fun withRealtimeModule(
        upgradeDiagnostics: UpgradeDiagnostics,
        headerTimeoutMs: Long = 300L,
        block: suspend (RecorderModule) -> Unit,
    ) {
        val moduleScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val fileLoggersBefore = Logging.loggers.filterIsInstance<FileLogger>()
        var module: RecorderModule? = null
        try {
            try {
                module = buildModule(moduleScope, upgradeDiagnostics, TestDispatcherProvider(Dispatchers.IO))
                    .apply { headerReadTimeoutMs = headerTimeoutMs }
                // Envelope: a regressed await must FAIL in seconds, not wedge a CI runner for 6h.
                // That happened — the pre-fix suite hung until the GitHub job timeout. The block's
                // real-time 300ms seam waits fit into this budget many times over.
                runBlocking { withTimeout(BLOCK_TIMEOUT_MS) { block(module) } }
            } finally {
                // Stop before cancelling: scope cancellation does NOT uninstall a running
                // recorder's global FileLogger. A wedged stop must not hang cleanup either; the
                // FileLogger assertion below then fails the test with the real signal.
                module?.let {
                    runBlocking {
                        try {
                            withTimeout(10_000) { it.stopRecorder() }
                        } catch (e: TimeoutCancellationException) {
                            // the leak assertion below reports it
                        }
                    }
                }
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

    @Test
    fun `a failing upgrade-diagnostics read still leaves a tracked recording`() = runTest {
        val diagnostics = mockk<UpgradeDiagnostics>()
        coEvery { diagnostics.debugInfo() } throws IllegalStateException("cache unreadable")

        val module = buildModule(backgroundScope, diagnostics)

        // Stopped in a finally: an assertion failing mid-test must not leave a live recorder whose
        // globally installed FileLogger then writes into every later test.
        var stopped: File? = null
        try {
            val logDir = module.startRecorder()
            logDir.exists() shouldBe true
            module.state.first { it.isRecording }.currentLogDir shouldBe logDir
        } finally {
            stopped = module.stopRecorder()
        }
        stopped.shouldNotBeNull()
    }

    /**
     * Cancellation is the one thing the guarded header read deliberately rethrows, so it is one of
     * the failures that can abort the state update. The recorder is already live at that point: it
     * has to be stopped on the way out, or it keeps writing into a session the module no longer
     * tracks.
     *
     * The start is launched, not awaited: an aborted update never flips isRecording, so
     * startRecorder() stays suspended. The virtual-time delay is what lets the module's own
     * background collectors run to completion.
     */
    @Test
    fun `a cancelled upgrade-diagnostics read stops the recorder instead of leaking it`() = runTest {
        val fileLoggersBefore = Logging.loggers.filterIsInstance<FileLogger>()
        val diagnostics = mockk<UpgradeDiagnostics>()
        coEvery { diagnostics.debugInfo() } throws CancellationException("scope died mid-read")

        val module = buildModule(backgroundScope, diagnostics)

        backgroundScope.launch { module.startRecorder() }
        delay(1_000)

        coVerify { diagnostics.debugInfo() }
        module.state.first().isRecording shouldBe false
        module.currentLogDir.shouldBeNull()
        // The recorder that was already writing when the header aborted got stopped: its file
        // logger is no longer installed.
        Logging.loggers.filterIsInstance<FileLogger>() shouldBe fileLoggersBefore
    }

    /**
     * Same window as above, but for an ordinary failure instead of a cancellation. The header's
     * injected sources are individually guarded, so the escape path is one of the unguarded first
     * log lines — here the build description read.
     */
    @Test
    fun `a failing header read stops the uncommitted recorder`() = runTest {
        val fileLoggersBefore = Logging.loggers.filterIsInstance<FileLogger>()
        val diagnostics = mockk<UpgradeDiagnostics>()
        coEvery { diagnostics.debugInfo() } returns "BillingCache(...)"

        mockkObject(BuildConfigWrap)
        every { BuildConfigWrap.VERSION_DESCRIPTION } throws IllegalStateException("build info unreadable")

        // Own scope: the escaping exception fails the collector, which must not fail the test's own
        // scope. SupervisorJob keeps the module's state flow alive so it can be inspected after.
        val moduleScope = CoroutineScope(coroutineContext + SupervisorJob() + CoroutineExceptionHandler { _, _ -> })
        try {
            val module = buildModule(moduleScope, diagnostics)

            moduleScope.launch { module.startRecorder() }
            delay(1_000)

            module.state.first().isRecording shouldBe false
            module.currentLogDir.shouldBeNull()
            // Non-vacuity: without the guard's cleanup the started recorder's file logger would
            // still be installed here.
            Logging.loggers.filterIsInstance<FileLogger>() shouldBe fileLoggersBefore
        } finally {
            moduleScope.cancel()
            unmockkObject(BuildConfigWrap)
        }
    }

    /**
     * Debug recording is what a user reaches for when the app is ALREADY misbehaving, so a
     * diagnostics source that never answers must not be the thing that denies them the log.
     */
    @Test
    fun `a wedged upgrade diagnostics read does not hold up the recording`() {
        val diagnostics = mockk<UpgradeDiagnostics>()
        coEvery { diagnostics.debugInfo() } coAnswers { awaitCancellation() }

        withRealtimeModule(diagnostics, headerTimeoutMs = 300L) { module ->
            val elapsed = measureTimeMillis { module.startRecorder() }

            module.state.first().isRecording shouldBe true
            logLines.any { it.startsWith("Upgrade diagnostics unavailable") } shouldBe true
            // Non-vacuity: without the bound this would sit on the wedged read forever.
            elapsed shouldBeLessThan 1_500L
        }
    }

    @Test
    fun `a flavor without diagnostics is not reported as unavailable`() {
        // FOSS has nothing to report and returns null: no diagnostics line at all, and above all
        // not one claiming the read failed or timed out.
        val diagnostics = mockk<UpgradeDiagnostics>()
        coEvery { diagnostics.debugInfo() } returns null

        withRealtimeModule(diagnostics) { module ->
            module.startRecorder()

            module.state.first().isRecording shouldBe true
            logLines.any { it.startsWith("Upgrade diagnostics") } shouldBe false
        }
    }

    companion object {
        private const val BLOCK_TIMEOUT_MS = 15_000L
    }
}
