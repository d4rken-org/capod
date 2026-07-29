package eu.darken.capod.common.debug.recording.core

import androidx.test.core.app.ApplicationProvider
import eu.darken.capod.common.InstallId
import eu.darken.capod.common.SystemTimeSource
import eu.darken.capod.common.upgrade.UpgradeDiagnostics
import eu.darken.capod.main.core.CurriculumVitae
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication
import testhelpers.coroutine.TestDispatcherProvider

/**
 * The recording header reads two independent diagnostics sources. Both reads happen AFTER the
 * recorder is already writing, so a failure in either must never abort the state update — that
 * would leave a running recorder the module no longer knows about, i.e. a debug recording that
 * can't be stopped or collected.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class RecorderModuleDiagnosticsTest : BaseTest() {

    private fun buildModule(
        scope: kotlinx.coroutines.CoroutineScope,
        curriculumVitae: CurriculumVitae,
        upgradeDiagnostics: UpgradeDiagnostics,
    ) = RecorderModule(
        context = ApplicationProvider.getApplicationContext(),
        appScope = scope,
        dispatcherProvider = TestDispatcherProvider(),
        installId = mockk<InstallId>(relaxed = true),
        timeSource = SystemTimeSource,
        curriculumVitae = curriculumVitae,
        upgradeDiagnostics = upgradeDiagnostics,
    )

    @Test
    fun `a failing pro-history read still leaves a tracked recording`() = runTest {
        val cv = mockk<CurriculumVitae>()
        coEvery { cv.proHistory() } throws IllegalStateException("history unreadable")
        val diagnostics = mockk<UpgradeDiagnostics>()
        coEvery { diagnostics.debugInfo() } returns "BillingCache(...)"

        val module = buildModule(backgroundScope, cv, diagnostics)

        module.startRecorder().shouldNotBeNull()
        module.state.first { it.isRecording }.currentLogDir.shouldNotBeNull()
        // The other source is independent: its evidence must still be collected.
        coVerify { diagnostics.debugInfo() }

        module.stopRecorder().shouldNotBeNull()
    }

    @Test
    fun `a failing upgrade-diagnostics read still leaves a tracked recording`() = runTest {
        val cv = mockk<CurriculumVitae>()
        coEvery { cv.proHistory() } returns CurriculumVitae.ProHistory(
            lastState = null,
            graceEngagedCount = 0,
            graceEngagedLast = null,
            proLostCount = 0,
            proLostLast = null,
        )
        val diagnostics = mockk<UpgradeDiagnostics>()
        coEvery { diagnostics.debugInfo() } throws IllegalStateException("cache unreadable")

        val module = buildModule(backgroundScope, cv, diagnostics)

        module.startRecorder().shouldNotBeNull()
        module.state.first { it.isRecording }.currentLogDir.shouldNotBeNull()
        coVerify { cv.proHistory() }

        module.stopRecorder().shouldNotBeNull()
    }

    @Test
    fun `both reads failing still leaves a tracked recording`() = runTest {
        val cv = mockk<CurriculumVitae>()
        coEvery { cv.proHistory() } throws IllegalStateException("history unreadable")
        val diagnostics = mockk<UpgradeDiagnostics>()
        coEvery { diagnostics.debugInfo() } throws IllegalStateException("cache unreadable")

        val module = buildModule(backgroundScope, cv, diagnostics)

        val logDir = module.startRecorder()
        logDir.exists() shouldBe true
        module.state.first { it.isRecording }.currentLogDir shouldBe logDir

        module.stopRecorder().shouldNotBeNull()
    }
}
