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
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
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
 * The flag collector in [RecorderModule]'s init mirrors `isRecording` from the module state, and a
 * committed stop is a value it legitimately publishes. Delivery of that value is not tied to the
 * recording it describes: the collector runs on the app scope while the recorder work runs on the
 * producer's IO context, so the `false` can land after the next session's [Recorder.start] has
 * already written `true`. `distinctUntilChangedBy`/`drop(1)` do not filter it — a genuine stop and
 * a genuine start are distinct values, and both pass.
 *
 * The window that follows is not short: the state that would repair the flag only commits after
 * `logRecordingHeader()`, which is bounded at five seconds.
 *
 * Forced rather than waited for: the module's app scope gets a dispatcher whose queue this test
 * steps through by hand, which is what a saturated Dispatchers.Default does to the flag collector's
 * continuation while the module's own producer proceeds on IO.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class RecorderModuleStaleStopFlagTest : BaseTest() {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val triggerFile: File
        get() = File(context.getExternalFilesDir(null), "capod_force_debug_run")

    private val externalLogsDir: File
        get() = File(context.getExternalFilesDir(null), "debug/logs")

    private val secondSessionDir: File
        get() = File(context.cacheDir, "stale-stop-second-session")

    @Before
    fun cleanRecorderFiles() {
        triggerFile.delete()
        externalLogsDir.deleteRecursively()
        File(context.cacheDir, "debug/logs").deleteRecursively()
        secondSessionDir.deleteRecursively()
        secondSessionDir.mkdirs()
    }

    @After
    fun resetDebugFlag() {
        Logging.loggers.filterIsInstance<FileLogger>().forEach { Logging.remove(it) }
        secondSessionDir.deleteRecursively()
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
    fun `a late committed stop does not clear the flag of the session that replaced it`() {
        val moduleDispatcher = SteppingDispatcher()
        val moduleScope = CoroutineScope(moduleDispatcher + SupervisorJob())
        val callerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        val module = RecorderModule(
            context = context,
            appScope = moduleScope,
            dispatcherProvider = TestDispatcherProvider(Dispatchers.IO),
            installId = mockk<InstallId>(relaxed = true),
            timeSource = SystemTimeSource,
            upgradeDiagnostics = mockk<UpgradeDiagnostics>().apply { coEvery { debugInfo() } returns null },
        )

        // Stands in for the recorder of the session that follows. In production this is the module's
        // own next recorder: its start() runs on the producer's IO context, not on the app scope
        // whose queue is held below, so it can write the flag while the collector still owes a value.
        val nextSession = Recorder(SystemTimeSource)

        try {
            runBlocking { withTimeout(AWAIT_TIMEOUT_MS) { withContext(Dispatchers.IO) { module.startRecorder() } } }
            settleAndHold(moduleDispatcher)
            withClue("the first recording is live, so the flag is true before anything is held") {
                Bugs.isDebug.value shouldBe true
            }

            val stop = callerScope.async { module.stopRecorder() }

            // The stop request has been published and the state machine collector is queued on it.
            runBlocking {
                withTimeout(AWAIT_TIMEOUT_MS) {
                    while (moduleDispatcher.heldCount() < 1) delay(POLL_MS)
                }
            }

            // Step 1: the state machine collector. It hands the stop to the module's own producer,
            // which stops the recorder on IO and publishes the committed stop.
            withClue("the state machine collector's turn on the stop request must be pending") {
                moduleDispatcher.releaseOne(AWAIT_TIMEOUT_MS) shouldBe true
            }
            runBlocking { withTimeout(AWAIT_TIMEOUT_MS) { stop.await() } }

            withClue("the recorder stopped itself, so nothing is recording at this point") {
                Bugs.isDebug.value shouldBe false
            }

            // The committed stop is queued for the flag collector and has not been delivered yet.
            withClue("the collector must still owe a delivery, or this test proves nothing") {
                moduleDispatcher.heldCount() shouldBeGreaterThanOrEqual 1
            }

            runBlocking { nextSession.start(File(secondSessionDir, "core.log")) }
            withClue("the new session's recorder wrote the flag next to the logger it installed") {
                Bugs.isDebug.value shouldBe true
            }

            // Step 2: everything the collector still owes, the committed stop included.
            moduleDispatcher.release()
            moduleDispatcher.barrier(AWAIT_TIMEOUT_MS) shouldBe true

            withClue("a live recorder is writing to the log file, so isDebug must not read false") {
                Bugs.isDebug.value shouldBe true
            }
        } finally {
            moduleDispatcher.release()
            runBlocking {
                runCatching { withTimeout(AWAIT_TIMEOUT_MS) { nextSession.stop() } }
                runCatching {
                    withTimeout(AWAIT_TIMEOUT_MS) { withContext(Dispatchers.IO) { module.stopRecorder() } }
                }
            }
            callerScope.cancel()
            moduleScope.cancel()
            moduleDispatcher.shutdown()
        }
    }

    /**
     * Holds the dispatcher at a point where nothing is queued on it, so the first task released
     * afterwards is the first reaction to whatever the test does next.
     */
    private fun settleAndHold(dispatcher: SteppingDispatcher) = runBlocking {
        withTimeout(AWAIT_TIMEOUT_MS) {
            while (true) {
                dispatcher.barrier(AWAIT_TIMEOUT_MS) shouldBe true
                dispatcher.hold()
                dispatcher.barrier(AWAIT_TIMEOUT_MS) shouldBe true
                delay(SETTLE_MS)
                if (dispatcher.heldCount() == 0) return@withTimeout
                dispatcher.release()
                delay(SETTLE_MS)
            }
        }
    }

    companion object {
        private const val AWAIT_TIMEOUT_MS = 5_000L
        private const val POLL_MS = 10L
        private const val SETTLE_MS = 100L
    }
}
