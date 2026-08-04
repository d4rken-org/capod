package eu.darken.capod.common.debug.recording.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import eu.darken.capod.common.BuildConfigWrap
import eu.darken.capod.common.InstallId
import eu.darken.capod.common.SystemTimeSource
import eu.darken.capod.common.TimeSource
import eu.darken.capod.common.debug.logging.FileLogger
import eu.darken.capod.common.debug.logging.Logging
import eu.darken.capod.common.upgrade.UpgradeDiagnostics
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication
import testhelpers.TestTimeSource
import testhelpers.coroutine.TestDispatcherProvider
import java.io.File
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Starting a recording is several steps wide — create a directory, start a recorder, persist the
 * trigger, write the header — and only the last of them commits the recorder into the module's
 * state. A failure in that window used to escape the reactive collector, which killed the collector
 * for the rest of the process: the recorder kept writing where nothing could stop it, the trigger
 * file survived to re-attempt the dead session on every launch, and startRecorder() waited for a
 * state nobody would ever publish. The debug log toggle was then dead until the app was reinstalled.
 *
 * A start that cannot succeed has to fail LOUDLY and leave the module usable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class RecorderModuleStartFailureTest : BaseTest() {

    private val headerReads = AtomicInteger(0)
    private var buildConfigMocked = false

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val triggerFile: File
        get() = File(context.getExternalFilesDir(null), "capod_force_debug_run")

    private val externalLogsDir: File
        get() = File(context.getExternalFilesDir(null), "debug/logs")

    @Before
    fun cleanRecorderFiles() {
        triggerFile.delete()
        externalLogsDir.deleteRecursively()
        File(context.cacheDir, "debug/logs").deleteRecursively()
    }

    @After
    fun restoreBuildConfig() {
        if (buildConfigMocked) unmockkObject(BuildConfigWrap)
    }

    /**
     * The one read in the start work that is deliberately NOT guarded: the header's build
     * description. Everything the header pulls from injected sources is caught and downgraded to a
     * warning, so it cannot drive this window at all — see [RecorderModuleDiagnosticsTest].
     *
     * Doubles as the attempt counter: [headerReads] tells a single failed attempt apart from a
     * collector that keeps re-entering the start branch.
     */
    private fun failTheHeaderRead() {
        mockkObject(BuildConfigWrap)
        buildConfigMocked = true
        every { BuildConfigWrap.VERSION_DESCRIPTION } answers {
            headerReads.incrementAndGet()
            throw IllegalStateException("build info unreadable")
        }
    }

    private fun repairTheHeaderRead() {
        every { BuildConfigWrap.VERSION_DESCRIPTION } answers {
            headerReads.incrementAndGet()
            "v1.2.3 (4) ~ FOSS/DEV"
        }
    }

    private inner class Modules(
        val scope: CoroutineScope,
        private val timeSource: TimeSource,
        private val upgradeDiagnostics: UpgradeDiagnostics,
    ) {
        val created = mutableListOf<RecorderModule>()

        fun create(recorderFactory: (() -> Recorder)? = null): RecorderModule = RecorderModule(
            context = ApplicationProvider.getApplicationContext(),
            appScope = scope,
            dispatcherProvider = TestDispatcherProvider(Dispatchers.IO),
            installId = mockk<InstallId>(relaxed = true),
            timeSource = timeSource,
            upgradeDiagnostics = upgradeDiagnostics,
        ).also { module ->
            recorderFactory?.let { module.recorderFactory = it }
            created.add(module)
        }

        // A REAL manager on the module's own scope: its reconciliation is a live collector reacting
        // to every recorder state, and the window this file is about only exists while it runs.
        // A static scan cannot show whether that collector zips a directory it should not.
        fun createManager(module: RecorderModule, zipper: DebugLogZipper) = DebugSessionManager(
            appScope = scope,
            dispatcherProvider = TestDispatcherProvider(Dispatchers.IO),
            recorderModule = module,
            debugLogZipper = zipper,
        )
    }

    /**
     * Real dispatchers: the module drives the start from its own scope, and the whole point here is
     * that a failed start settles instead of hanging — virtual time would hide a wedge rather than
     * expose it. The envelope is what turns a regression into a failure in seconds instead of a CI
     * runner stuck until the job timeout, which is what the pre-fix module did.
     */
    private fun withModules(
        timeSource: TimeSource = SystemTimeSource,
        upgradeDiagnostics: UpgradeDiagnostics = mockk<UpgradeDiagnostics>().apply {
            coEvery { debugInfo() } returns null
        },
        block: suspend (Modules) -> Unit,
    ) {
        val moduleScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val fileLoggersBefore = Logging.loggers.filterIsInstance<FileLogger>()
        val modules = Modules(moduleScope, timeSource, upgradeDiagnostics)
        try {
            try {
                runBlocking { withTimeout(BLOCK_TIMEOUT_MS) { block(modules) } }
            } finally {
                // Stop before cancelling: scope cancellation does NOT uninstall a running
                // recorder's global FileLogger. A wedged stop must not hang cleanup either; the
                // FileLogger assertion below then fails the test with the real signal.
                modules.created.forEach { module ->
                    runBlocking {
                        try {
                            withTimeout(STOP_TIMEOUT_MS) { module.stopRecorder() }
                        } catch (e: Exception) {
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

    // A session the module resumes at startup: the real two-line trigger file plus an existing dir.
    private fun seedResumableSession(startTime: Long): File {
        val sessionDir = File(externalLogsDir, "capod_1.0_20260101T000000Z_seeded").also { it.mkdirs() }
        File(sessionDir, "core.log").writeText("earlier recording\n")
        triggerFile.writeText("${sessionDir.absolutePath}\n$startTime")
        return sessionDir
    }

    @Test
    fun `a failed start surfaces the error instead of hanging`() {
        failTheHeaderRead()

        withModules { modules ->
            val module = modules.create()

            val error = shouldThrow<IllegalStateException> { module.startRecorder() }
            error.message shouldBe "build info unreadable"

            val state = module.state.first()
            state.isRecording shouldBe false
            // Reset on failure, or the every-state collector walks straight back into the start
            // branch and retries forever.
            state.shouldRecord shouldBe false
            state.currentLogDir.shouldBeNull()
            state.recordingStartedAt shouldBe 0L
            state.startFailure.shouldNotBeNull()
            module.currentLogDir.shouldBeNull()
            // A trigger left behind would re-attempt this dead session on every app launch.
            triggerFile.exists() shouldBe false
        }
    }

    @Test
    fun `the recorder recovers after a failed start`() {
        failTheHeaderRead()

        withModules { modules ->
            val module = modules.create()
            shouldThrow<IllegalStateException> { module.startRecorder() }

            repairTheHeaderRead()

            val logDir = module.startRecorder()
            logDir.exists() shouldBe true
            val recording = module.state.first { it.isRecording }
            recording.currentLogDir shouldBe logDir
            // A stale failure must not be reported as the outcome of the attempt that succeeded.
            recording.startFailure.shouldBeNull()
            module.currentLogDir shouldBe logDir
            triggerFile.exists() shouldBe true

            module.stopRecorder() shouldBe logDir
            module.state.first().isRecording shouldBe false
            triggerFile.exists() shouldBe false
        }
    }

    /**
     * A [CancellationException] out of the start work does NOT mean this module's scope is going
     * away — a bounded read timing out looks exactly like this. Rethrowing it would kill the state
     * collector permanently, which is the very wedge this class exists to prevent, so it is
     * converted into an ordinary failure instead.
     */
    @Test
    fun `a foreign cancellation during start does not kill the recorder for good`() {
        // Atomic: the read runs on the module's own thread, the test flips it from its own.
        val readFailure = AtomicReference<Throwable?>(CancellationException("bounded read gave up"))
        val diagnostics = mockk<UpgradeDiagnostics>()
        coEvery { diagnostics.debugInfo() } coAnswers {
            readFailure.get()?.let { throw it }
            null
        }

        withModules(upgradeDiagnostics = diagnostics) { modules ->
            val module = modules.create()

            // Not a CancellationException: handing that to the caller would cancel THEM.
            shouldThrow<RecorderModule.RecordingStartFailedException> { module.startRecorder() }
            module.state.first().isRecording shouldBe false

            readFailure.set(null)

            // Non-vacuity: with a rethrow the collector would be dead here and this would hang
            // until the envelope kills the test.
            val logDir = module.startRecorder()
            logDir.exists() shouldBe true
            module.state.first { it.isRecording }.currentLogDir shouldBe logDir
        }
    }

    @Test
    fun `a failed start is attempted once and then settles`() {
        failTheHeaderRead()

        withModules { modules ->
            val module = modules.create()

            shouldThrow<IllegalStateException> { module.startRecorder() }
            headerReads.get() shouldBe 1

            // The collector reacts to EVERY state emission and re-derives the branch from
            // shouldRecord, so a failure that left shouldRecord set would spin here.
            delay(SETTLE_MS)
            headerReads.get() shouldBe 1
            module.state.first().shouldRecord shouldBe false

            // One attempt per request, not one per state emission.
            shouldThrow<IllegalStateException> { module.startRecorder() }
            headerReads.get() shouldBe 2
            delay(SETTLE_MS)
            headerReads.get() shouldBe 2
        }
    }

    /**
     * The start is only committed into the state once the recorder is live, so for the whole window
     * before that the session dir sits on disk with nothing pointing at it: a scan sees a directory
     * with a non-empty core.log and no sibling zip, which is exactly an orphan. A live manager
     * scanning in that window would compress the directory the recorder is writing into, and the
     * rollback of a failing start would then race the zipper — with a leftover archive left behind
     * to hand the retry the very session ID that just died.
     *
     * The header read is blocked to hold the window open, the same seam [RecorderModuleDiagnosticsTest]
     * uses to exercise it.
     */
    @Test
    fun `a live manager does not zip a session dir while its start is still in flight`() {
        val headerBlocked = CountDownLatch(1)
        val releaseHeader = CountDownLatch(1)
        mockkObject(BuildConfigWrap)
        buildConfigMocked = true
        every { BuildConfigWrap.VERSION_DESCRIPTION } answers {
            headerReads.incrementAndGet()
            headerBlocked.countDown()
            releaseHeader.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            throw IllegalStateException("build info unreadable")
        }

        val zipped = CopyOnWriteArrayList<File>()
        val zipper = mockk<DebugLogZipper>()
        every { zipper.zip(any()) } answers {
            val dir = firstArg<File>()
            zipped.add(dir)
            // Produce the archive for real: without it the reconciliation would find the same
            // orphan on every rescan and spin.
            File(dir.parentFile, "${dir.name}.zip").also { it.writeText("zipped") }
        }

        withModules { modules ->
            val module = modules.create()
            val manager = modules.createManager(module, zipper)

            val start = modules.scope.async { module.startRecorder() }
            withContext(Dispatchers.IO) { headerBlocked.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS) } shouldBe true

            val inFlight = externalLogsDir.listFiles()?.toList().orEmpty().single { it.isDirectory }
            // The recorder is already writing, so this is a Ready orphan to a scan — not a broken
            // session it would skip anyway.
            File(inFlight, "core.log").length() shouldBeGreaterThan 0L

            // A finished session from before: the gate defers it too, but it is not the one at risk,
            // so it doubles as the proof that the collector is alive and reaches the auto-zip path.
            val bystander = File(externalLogsDir, "capod_1.0_20250101T000000Z_bystander").also { it.mkdirs() }
            File(bystander, "core.log").writeText("an earlier session\n")

            // The delayed scan of the failure: it runs while the start is still pending, so the
            // in-flight dir has no activeDir to match and looks like everybody else's leftovers.
            manager.refresh()
            val seen = withTimeout(AWAIT_TIMEOUT_MS) {
                manager.sessions.first { scan -> scan.any { it.displayName == inFlight.name } }
            }
            // Non-vacuity: the manager really did scan both dirs, and in a shape its reconciliation
            // would have zipped. The gate is what stopped it, not a scan that never happened.
            seen shouldHaveSize 2
            seen.forEach { it.shouldBeInstanceOf<DebugSession.Ready>() }

            delay(SETTLE_MS)
            zipped.shouldBeEmpty()

            releaseHeader.countDown()
            shouldThrow<IllegalStateException> { start.await() }
            module.state.first { it.startFailure != null }

            // The rollback ran to completion, uncontested: no zipper had a claim on the directory.
            inFlight.exists() shouldBe false
            File(externalLogsDir, "${inFlight.name}.zip").exists() shouldBe false

            // And the gate lifts with the settled state — deferred, not cancelled.
            val bystanderZip = File(externalLogsDir, "${bystander.name}.zip")
            withTimeout(AWAIT_TIMEOUT_MS) {
                while (!bystanderZip.exists()) delay(POLL_MS)
            }
            zipped.toList() shouldBe listOf(bystander)
        }
    }

    @Test
    fun `a second recording in the same second gets its own session dir`() {
        withModules(timeSource = TestTimeSource(elapsedRealtimeMs = 100_000L)) { modules ->
            val module = modules.create()

            val first = module.startRecorder()
            module.stopRecorder() shouldBe first

            // Same fixed clock, so the name is identical — appending into the finished session
            // would interleave two recordings in one core.log.
            val second = module.startRecorder()
            second shouldNotBe first
            first.exists() shouldBe true
            second.exists() shouldBe true
        }
    }

    /**
     * A zipped session keeps its name as an archive after its directory is gone, and a zip still
     * being written keeps it as a '.zip.tmp'. The session ID is derived from that name, so a retry
     * within the same second that reused it would hand a live recording the identity of an archive
     * that already exists — and whatever the user then shares is the wrong one.
     */
    @Test
    fun `a session name left behind by an archive is not reused`() {
        withModules(timeSource = TestTimeSource(elapsedRealtimeMs = 100_000L)) { modules ->
            val module = modules.create()

            val first = module.startRecorder()
            module.stopRecorder() shouldBe first

            first.deleteRecursively() shouldBe true
            File(externalLogsDir, "${first.name}.zip").writeText("archived")
            File(externalLogsDir, "${first.name}_2.zip.tmp").writeText("half an archive")

            val second = module.startRecorder()
            second.name shouldBe "${first.name}_3"
            second.exists() shouldBe true
            DebugSessionManager.deriveSessionId(second) shouldNotBe DebugSessionManager.deriveSessionId(first)
        }
    }

    /**
     * The stop side of the same window: a recorder that cannot stop must not strand the state.
     * Everything awaiting the transition — stopRecorder(), requestStopRecorder(), the UI's
     * isRecording — depends on the cleared state being committed anyway.
     */
    @Test
    fun `a recorder that fails to stop still clears the recording state`() {
        val brokenRecorder = mockk<Recorder>(relaxed = true)
        coEvery { brokenRecorder.stop() } throws IOException("log writer wedged")

        withModules { modules ->
            val module = modules.create(recorderFactory = { brokenRecorder })

            val logDir = module.startRecorder()
            triggerFile.exists() shouldBe true

            module.stopRecorder() shouldBe logDir

            val state = module.state.first()
            state.isRecording shouldBe false
            state.currentLogDir.shouldBeNull()
            state.recordingStartedAt shouldBe 0L
            state.recordingStartedAtMonotonic.shouldBeNull()
            module.currentLogDir.shouldBeNull()
            triggerFile.exists() shouldBe false
        }
    }

    /**
     * The boot path starts a recording without anyone calling startRecorder(): a trigger file left
     * from a previous run makes the module resume on construction. If that resume fails and the
     * trigger survives, every single launch re-attempts the same dead session.
     */
    @Test
    fun `a failed resume at boot clears the trigger instead of retrying every launch`() {
        val timeSource = TestTimeSource(elapsedRealtimeMs = 100_000L)
        val sessionDir = seedResumableSession(timeSource.currentTimeMillis() - 20_000L)
        failTheHeaderRead()

        withModules(timeSource = timeSource) { modules ->
            val module = modules.create()

            module.state.first { it.startFailure != null }
            module.state.first().isRecording shouldBe false
            module.currentLogDir.shouldBeNull()
            triggerFile.exists() shouldBe false
            // A resumed dir holds a real earlier recording — rollback deletes only what it created.
            sessionDir.exists() shouldBe true
            File(sessionDir, "core.log").exists() shouldBe true
            headerReads.get() shouldBe 1

            // The next launch: nothing left to resume, so no second attempt at the dead session.
            val nextLaunch = modules.create()
            nextLaunch.state.first().shouldRecord shouldBe false
            delay(SETTLE_MS)
            headerReads.get() shouldBe 1
        }
    }

    companion object {
        private const val BLOCK_TIMEOUT_MS = 15_000L
        private const val STOP_TIMEOUT_MS = 10_000L

        // Real time, not virtual: long enough for a retry loop to show itself, short enough to stay
        // well inside the block envelope.
        private const val SETTLE_MS = 500L

        // Waiting for something a live collector has to do. Bounded so a regression reports the
        // step that never happened instead of burning the whole block envelope.
        private const val AWAIT_TIMEOUT_MS = 5_000L
        private const val POLL_MS = 25L
    }
}
