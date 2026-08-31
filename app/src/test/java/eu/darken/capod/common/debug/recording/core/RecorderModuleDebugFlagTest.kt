package eu.darken.capod.common.debug.recording.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import eu.darken.capod.common.InstallId
import eu.darken.capod.common.SystemTimeSource
import eu.darken.capod.common.debug.Bugs
import eu.darken.capod.common.debug.logging.FileLogger
import eu.darken.capod.common.debug.logging.Logging
import eu.darken.capod.common.upgrade.UpgradeDiagnostics
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
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
import testhelpers.coroutine.TestDispatcherProvider
import java.io.File
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

/**
 * [Bugs.isDebug] has two writers: [Recorder.start]/[Recorder.stop] flip it around the file logger
 * they install, and a collector in [RecorderModule]'s init mirrors `isRecording` from every state
 * emission. Three of those emissions carry an `isRecording` the recorder has already moved past —
 * a start request is published while `recorder` is still null, a stop request while it is still
 * set — so the collector can publish a value that contradicts the logger that is actually running.
 *
 * The start request is the expensive one: the state that would repair it only commits after the
 * recording header has been read, which is bounded at five seconds. Everything logged in that
 * window loses the debug-only diagnostics that key off this flag.
 *
 * The two writers run on the same scope but different dispatchers, so which one lands last is a
 * race. It is forced here rather than waited for: the module's scope gets a dispatcher whose queue
 * this test steps through by hand, which is what a saturated Dispatchers.Default does to the flag
 * collector's continuation while the start work proceeds on an IO thread.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class RecorderModuleDebugFlagTest : BaseTest() {

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
    fun resetDebugFlag() {
        Logging.loggers.filterIsInstance<FileLogger>().forEach { Logging.remove(it) }
        Bugs.isDebug.value = false
    }

    /**
     * A single-threaded dispatcher whose queue can be held and then stepped through one task at a
     * time. [releaseOne] returns once that task has run to its next suspension point, so a step is
     * a decision point and not a sleep.
     */
    private class SteppingDispatcher : CoroutineDispatcher() {
        private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "stepping-dispatcher") }
        private val held = ArrayDeque<Runnable>()
        private var open = true

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            synchronized(held) {
                if (open) executor.execute(block) else held.addLast(block)
            }
        }

        fun hold() = synchronized(held) { open = false }

        fun release() = synchronized(held) {
            open = true
            while (held.isNotEmpty()) executor.execute(held.removeFirst())
        }

        fun heldCount(): Int = synchronized(held) { held.size }

        fun releaseOne(timeoutMs: Long): Boolean {
            val block = synchronized(held) { held.pollFirst() } ?: return false
            val done = CountDownLatch(1)
            executor.execute {
                try {
                    block.run()
                } finally {
                    done.countDown()
                }
            }
            return done.await(timeoutMs, TimeUnit.MILLISECONDS)
        }

        /** Runs after everything already queued: a barrier for "the collectors have settled". */
        fun barrier(timeoutMs: Long): Boolean {
            val done = CountDownLatch(1)
            executor.execute { done.countDown() }
            return done.await(timeoutMs, TimeUnit.MILLISECONDS)
        }

        fun shutdown() {
            executor.shutdownNow()
        }
    }

    @Test
    fun `the flag collector does not clobber the flag of a recorder that is already live`() {
        val moduleDispatcher = SteppingDispatcher()
        val moduleScope = CoroutineScope(moduleDispatcher + SupervisorJob())
        val callerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        // Mirrors the real recorder: the flag is flipped where the file logger is installed, and
        // parked there so the module cannot commit the state that would paper over a stale write.
        val recorderLive = CountDownLatch(1)
        val releaseStart = CountDownLatch(1)
        val recorder = mockk<Recorder>(relaxed = true)
        coEvery { recorder.start(any()) } coAnswers {
            Bugs.isDebug.value = true
            recorderLive.countDown()
            releaseStart.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            Unit
        }
        coEvery { recorder.stop() } coAnswers { Bugs.isDebug.value = false }

        val module = RecorderModule(
            context = context,
            appScope = moduleScope,
            dispatcherProvider = TestDispatcherProvider(Dispatchers.IO),
            installId = mockk<InstallId>(relaxed = true),
            timeSource = SystemTimeSource,
            upgradeDiagnostics = mockk<UpgradeDiagnostics>().apply { coEvery { debugInfo() } returns null },
        ).apply { recorderFactory = { recorder } }

        try {
            // Both init collectors have subscribed and consumed the initial state.
            moduleDispatcher.barrier(AWAIT_TIMEOUT_MS) shouldBe true
            runBlocking { withTimeout(AWAIT_TIMEOUT_MS) { module.state.first() } }.isRecording shouldBe false
            moduleDispatcher.barrier(AWAIT_TIMEOUT_MS) shouldBe true

            moduleDispatcher.hold()
            val start = callerScope.async { module.startRecorder() }

            // The start request has been published: both collectors are queued on it.
            runBlocking {
                withTimeout(AWAIT_TIMEOUT_MS) {
                    while (moduleDispatcher.heldCount() < 2) delay(POLL_MS)
                }
            }

            // Step 1: the state machine collector, which hands the start work to the module's own
            // producer and leaves the recorder live but uncommitted.
            moduleDispatcher.releaseOne(AWAIT_TIMEOUT_MS) shouldBe true
            recorderLive.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS) shouldBe true
            withClue("the recorder's own write is the baseline this test measures against") {
                Bugs.isDebug.value shouldBe true
            }

            // Step 2: the flag collector, still holding the pre-start emission.
            withClue("the flag collector's turn on the start request must still be pending") {
                moduleDispatcher.releaseOne(AWAIT_TIMEOUT_MS) shouldBe true
            }

            withClue("a live recorder is writing to the log file, so isDebug must not read false") {
                Bugs.isDebug.value shouldBe true
            }

            releaseStart.countDown()
            moduleDispatcher.release()
            runBlocking { withTimeout(AWAIT_TIMEOUT_MS) { start.await() } }
        } finally {
            releaseStart.countDown()
            moduleDispatcher.release()
            runBlocking {
                try {
                    withTimeout(AWAIT_TIMEOUT_MS) { withContext(Dispatchers.IO) { module.stopRecorder() } }
                } catch (e: Exception) {
                    // cleanup only
                }
            }
            callerScope.cancel()
            moduleScope.cancel()
            moduleDispatcher.shutdown()
        }
    }

    companion object {
        private const val AWAIT_TIMEOUT_MS = 5_000L
        private const val POLL_MS = 10L
    }
}
