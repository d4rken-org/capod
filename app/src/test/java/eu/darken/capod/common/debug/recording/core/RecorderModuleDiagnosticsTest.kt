package eu.darken.capod.common.debug.recording.core

import androidx.test.core.app.ApplicationProvider
import eu.darken.capod.common.BuildConfigWrap
import eu.darken.capod.common.InstallId
import eu.darken.capod.common.SystemTimeSource
import eu.darken.capod.common.debug.logging.FileLogger
import eu.darken.capod.common.debug.logging.Logging
import eu.darken.capod.common.upgrade.UpgradeDiagnostics
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication
import testhelpers.coroutine.TestDispatcherProvider

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

    private fun buildModule(
        scope: CoroutineScope,
        upgradeDiagnostics: UpgradeDiagnostics,
    ) = RecorderModule(
        context = ApplicationProvider.getApplicationContext(),
        appScope = scope,
        dispatcherProvider = TestDispatcherProvider(),
        installId = mockk<InstallId>(relaxed = true),
        timeSource = SystemTimeSource,
        upgradeDiagnostics = upgradeDiagnostics,
    )

    @Test
    fun `a failing upgrade-diagnostics read still leaves a tracked recording`() = runTest {
        val diagnostics = mockk<UpgradeDiagnostics>()
        coEvery { diagnostics.debugInfo() } throws IllegalStateException("cache unreadable")

        val module = buildModule(backgroundScope, diagnostics)

        val logDir = module.startRecorder()
        logDir.exists() shouldBe true
        module.state.first { it.isRecording }.currentLogDir shouldBe logDir

        module.stopRecorder().shouldNotBeNull()
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
}
