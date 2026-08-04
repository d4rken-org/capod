package eu.darken.capod.common.debug.recording.core

import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.annotation.VisibleForTesting
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.BuildConfigWrap
import eu.darken.capod.common.InstallId
import eu.darken.capod.common.SystemTimeSource
import eu.darken.capod.common.TimeSource
import eu.darken.capod.common.coroutine.AppScope
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.Bugs
import eu.darken.capod.common.debug.logging.Logging.Priority.ERROR
import eu.darken.capod.common.debug.logging.Logging.Priority.INFO
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.flow.DynamicStateFlow
import eu.darken.capod.common.upgrade.UpgradeDiagnostics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecorderModule @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val installId: InstallId,
    private val timeSource: TimeSource,
    private val upgradeDiagnostics: UpgradeDiagnostics,
) {

    // Test seam: the header read below is bounded on real dispatchers, so a virtual-time test cannot
    // advance the production bound. Same pattern as BillingCache.cacheTimeoutMs.
    internal var headerReadTimeoutMs: Long = HEADER_READ_TIMEOUT_MS

    // Test seam: the recorder is constructed inline, so a failure at start or stop — the window this
    // module's rollback exists for — has no other way in. Same pattern as [headerReadTimeoutMs].
    internal var recorderFactory: () -> Recorder = { Recorder(timeSource) }

    // Serializes the public start/stop entry points, observation included: two callers racing the
    // same transition would otherwise each await a state the other one is about to overwrite.
    private val startStopLock = Mutex()

    @Volatile
    internal var currentLogDir: File? = null
        private set

    private val triggerFile = try {
        File(context.getExternalFilesDir(null), FORCE_FILE)
    } catch (e: Exception) {
        File(
            Environment.getExternalStorageDirectory(),
            "/Android/data/${BuildConfigWrap.APPLICATION_ID}/files/$FORCE_FILE"
        )
    }

    private val internalState = DynamicStateFlow(TAG, appScope + dispatcherProvider.IO) {
        val triggerFileExists = triggerFile.exists()
        val persistedInfo = if (triggerFileExists) readTriggerFile() else null
        State(
            shouldRecord = triggerFileExists,
            persistedLogDir = persistedInfo?.first,
            recordingStartedAt = persistedInfo?.second ?: 0L,
        )
    }
    val state: Flow<State> = internalState.flow

    init {
        internalState.flow
            .onEach {
                log(TAG) { "New Recorder state: $it" }

                internalState.updateBlocking {
                    if (!isRecording && shouldRecord) {
                        // Everything between "a recorder exists" and "the state knows about it" is
                        // guarded: a throw anywhere in here would otherwise abandon a running
                        // recorder, kill this collector and wedge whoever awaits the state.
                        var newRecorder: Recorder? = null
                        var freshSessionDir: File? = null
                        try {
                            val isResume = persistedLogDir != null && persistedLogDir.exists()
                            val sessionDir = if (isResume) {
                                log(TAG, INFO) { "Resuming recording into existing session: $persistedLogDir" }
                                persistedLogDir
                            } else {
                                createSessionDir().also { freshSessionDir = it }
                            }
                            val logFile = File(sessionDir, "core.log")
                            val startedRecorder = recorderFactory().also { newRecorder = it }
                            startedRecorder.start(logFile)

                            val startTime = when {
                                !isResume -> timeSource.currentTimeMillis()
                                recordingStartedAt > 0L -> recordingStartedAt
                                else -> timeSource.currentTimeMillis()
                            }

                            if (!isResume) writeTriggerFile(sessionDir, startTime)

                            logRecordingHeader()

                            this@RecorderModule.currentLogDir = sessionDir

                            copy(
                                recorder = startedRecorder,
                                currentLogDir = sessionDir,
                                recordingStartedAt = startTime,
                                recordingStartedAtMonotonic = if (isResume) null else timeSource.elapsedRealtime(),
                                persistedLogDir = null,
                                startFailure = null,
                            )
                        } catch (e: Exception) {
                            // Roll back BEFORE deciding what the failure means: even a genuine
                            // cancellation must not leave the started recorder behind.
                            withContext(NonCancellable) {
                                newRecorder?.let {
                                    try {
                                        it.stop()
                                    } catch (stopError: Exception) {
                                        e.addSuppressed(stopError)
                                    }
                                }
                                this@RecorderModule.currentLogDir = null
                                // The trigger is written inside the guarded window and a resume
                                // reads a pre-existing one: leaving either behind re-attempts the
                                // dead session on every launch.
                                deleteTriggerFile()
                                // Only a dir WE created this attempt. Publishing the failure kicks
                                // off a session scan, and an empty dir left here would be
                                // auto-zipped as an orphan while the retry writes into it. A
                                // resumed dir is somebody's actual recording and is never deleted.
                                freshSessionDir?.let {
                                    if (it.exists() && !it.deleteRecursively()) {
                                        log(TAG, WARN) { "Failed to clean up session dir: $it" }
                                    }
                                }
                            }
                            // Our own scope dying is the one failure that SHOULD take this collector
                            // with it. Anything else — including a cancellation from inside the start
                            // work — becomes an ordinary failure, because rethrowing it here would
                            // kill the collector and wedge the module for the rest of the process.
                            currentCoroutineContext().ensureActive()

                            log(TAG, ERROR) { "Failed to start recording: ${e.asLog()}" }

                            copy(
                                shouldRecord = false,
                                startFailure = asStartFailure(e),
                                recorder = null,
                                currentLogDir = null,
                                recordingStartedAt = 0L,
                                recordingStartedAtMonotonic = null,
                                persistedLogDir = null,
                            )
                        }
                    } else if (!shouldRecord && isRecording) {
                        val stopError = try {
                            requireNotNull(recorder) { "Recorder is null despite isRecording" }.stop()
                            null
                        } catch (e: Exception) {
                            e
                        }

                        withContext(NonCancellable) {
                            deleteTriggerFile()
                            this@RecorderModule.currentLogDir = null
                        }

                        if (stopError != null) {
                            currentCoroutineContext().ensureActive()
                            // The state is cleared regardless: a stop that cannot complete must not
                            // also strand everyone awaiting the transition. A file logger that
                            // survived this is a leak, so it gets reported rather than hidden.
                            log(TAG, ERROR) { "Failed to stop recorder cleanly: ${stopError.asLog()}" }
                        }

                        copy(
                            recorder = null,
                            currentLogDir = null,
                            recordingStartedAt = 0L,
                            recordingStartedAtMonotonic = null,
                        )
                    } else {
                        this
                    }
                }
            }
            .launchIn(appScope)

        internalState.flow
            .onEach { Bugs.isDebug.value = it.isRecording }
            .launchIn(appScope)
    }

    // Header lines written into a freshly started recording. Runs AFTER the recorder is live, so
    // every read here is diagnostics-only and must never propagate: a failure that escapes costs
    // the user the whole recording, which the caller's start branch then has to roll back.
    private suspend fun logRecordingHeader() {
        log(TAG, INFO) { "Build.Fingerprint: ${Build.FINGERPRINT}" }
        log(TAG, INFO) { "BuildConfig.Versions: ${BuildConfigWrap.VERSION_DESCRIPTION}" }

        try {
            // Diagnostics only — a broken read must not stop the recorder from starting. Bounded on
            // top of that: debug recording is what a user reaches for when the app is ALREADY
            // misbehaving, so a source that never answers (a stuck DataStore file lock, a billing
            // store that doesn't respond) must not hold up the start of the recording either.
            val read = withTimeoutOrNull(headerReadTimeoutMs) { HeaderRead(upgradeDiagnostics.debugInfo()) }
            when {
                read == null -> log(TAG, WARN) {
                    "Upgrade diagnostics unavailable, read did not finish within ${headerReadTimeoutMs}ms"
                }
                // Completion is tracked separately from the value: a flavor that legitimately has
                // nothing to report (FOSS) returns null and gets no line at all, not an "unavailable".
                read.value != null -> log(TAG, INFO) { "Upgrade diagnostics: ${read.value}" }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, WARN) { "Upgrade diagnostics unavailable: ${e.asLog()}" }
        }
    }

    /**
     * Completion marker for a header read: tells a source that legitimately has nothing to report
     * (no diagnostics on FOSS) apart from one that never answered within the deadline.
     */
    private class HeaderRead<T>(val value: T)

    /**
     * A start failure that arrived as a [CancellationException] while this module's own scope was
     * still alive — a bounded read inside the start work timing out, for example. Handing that to
     * the caller unchanged would cancel THEM for a failure that is not theirs, so it is converted
     * into an ordinary one.
     */
    class RecordingStartFailedException(cause: Throwable) : IllegalStateException("Failed to start recording", cause)

    private fun asStartFailure(error: Exception): Throwable = when (error) {
        is CancellationException -> RecordingStartFailedException(error)
        else -> error
    }

    private fun deleteTriggerFile() {
        try {
            if (triggerFile.exists() && !triggerFile.delete()) {
                log(TAG, ERROR) { "Failed to delete trigger file" }
            }
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to delete trigger file: ${e.asLog()}" }
        }
    }

    private fun createSessionDir(): File {
        val timestamp = timeSource.now().atZone(java.time.ZoneOffset.UTC)
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))
        val installIdPrefix = installId.id.take(8)
        val baseName = "capod_${BuildConfigWrap.VERSION_NAME}_${timestamp}_$installIdPrefix"

        val primaryParent = try {
            val dir = File(context.getExternalFilesDir(null), "debug/logs")
            dir.mkdirs()
            if (dir.canWrite()) dir else null
        } catch (e: Exception) {
            log(TAG, WARN) { "External files dir unavailable: $e" }
            null
        }

        val parent = primaryParent ?: File(context.cacheDir, "debug/logs").also { it.mkdirs() }

        // The name is timestamped to the second, so two sessions within the same second — a retry
        // after a failed start, most of all — would land in one directory and interleave their logs.
        var sessionDir = File(parent, baseName)
        var collision = 1
        while (sessionDir.isNameTaken()) {
            collision++
            sessionDir = File(parent, "${baseName}_$collision")
        }
        sessionDir.mkdirs()

        log(TAG) { "Created session dir: $sessionDir" }
        return sessionDir
    }

    /**
     * The session ID is derived from this name, so a sibling archive claims it just as much as a
     * directory does: the dir of a zipped session is gone, and reusing its name would hand a new
     * recording the identity of the archive next to it. A '.zip.tmp' is a zip still being written.
     */
    private fun File.isNameTaken(): Boolean = exists() ||
        File(parentFile, "$name.zip").exists() ||
        File(parentFile, "$name.zip.tmp").exists()

    internal fun getLogDirectories(): List<File> = listOfNotNull(
        try {
            context.getExternalFilesDir(null)?.let { File(it, "debug/logs") }
        } catch (e: Exception) {
            null
        },
        File(context.cacheDir, "debug/logs"),
    )

    suspend fun startRecorder(): File = startStopLock.withLock {
        // Clearing the failure is part of the request: a stale one from an earlier attempt would
        // otherwise be reported as the outcome of this one.
        internalState.updateBlocking {
            copy(shouldRecord = true, startFailure = null)
        }
        // A start that cannot succeed has to settle the wait too, or the caller sits here forever.
        val state = internalState.flow.first { it.isRecording || it.startFailure != null }
        state.startFailure?.let { throw it }
        requireNotNull(state.currentLogDir) { "Recording state has no logDir" }
    }

    suspend fun stopRecorder(): File? = startStopLock.withLock { stopRecorderUnlocked() }

    private suspend fun stopRecorderUnlocked(): File? {
        val currentDir = internalState.value().currentLogDir ?: return null
        internalState.updateBlocking {
            copy(shouldRecord = false)
        }
        internalState.flow.filter { !it.isRecording }.first()
        return currentDir
    }

    suspend fun requestStopRecorder(): StopResult = startStopLock.withLock {
        val currentState = internalState.value()
        if (!currentState.isRecording) return@withLock StopResult.NotRecording

        val logDir = currentState.currentLogDir ?: return@withLock StopResult.NotRecording
        val startedAtMono = currentState.recordingStartedAtMonotonic
        val elapsed = if (startedAtMono != null) {
            // Live session: monotonic, immune to wall-clock adjustments mid-recording.
            timeSource.elapsedRealtime() - startedAtMono
        } else {
            // Resumed session: the trigger file persists wall time only — it has to survive reboots,
            // which monotonic time does not.
            timeSource.currentTimeMillis() - currentState.recordingStartedAt
        }
        // Negative = the wall clock moved backward across a resume; fail open (no warning) rather
        // than trap the user in TooShort.
        if (elapsed in 0 until MIN_RECORDING_MS) return@withLock StopResult.TooShort

        stopRecorderUnlocked()
        val sessionId = DebugSessionManager.deriveSessionId(logDir)
        StopResult.Stopped(logDir, sessionId)
    }

    sealed class StopResult {
        data object TooShort : StopResult()
        data class Stopped(val logDir: File, val sessionId: String) : StopResult()
        data object NotRecording : StopResult()
    }

    data class State(
        val shouldRecord: Boolean = false,
        internal val recorder: Recorder? = null,
        val currentLogDir: File? = null,
        val recordingStartedAt: Long = 0L,
        // Monotonic base for the duration heuristic, null when there is none: a resumed session's
        // only start time is the persisted wall clock, and a monotonic value from a previous
        // process or boot is meaningless.
        internal val recordingStartedAtMonotonic: Long? = null,
        internal val persistedLogDir: File? = null,
        // Why the last start attempt did not produce a recording. Carried as the Throwable itself:
        // two consecutive failures are distinct instances, so the state flow's distinctUntilChanged
        // cannot swallow the second one.
        val startFailure: Throwable? = null,
    ) {
        val isRecording: Boolean
            get() = recorder != null

        /**
         * A start that was requested but has not committed yet. Its session dir already exists on
         * disk while no field here points at it, so anything reconciling the log directory against
         * this state has to treat the window as "not settled" rather than as a stale leftover.
         */
        internal val isStartPending: Boolean
            get() = shouldRecord && !isRecording

        val currentLogPath: File?
            get() = recorder?.path
    }

    internal fun readTriggerFile(): Pair<File, Long>? = try {
        parseTriggerContent(triggerFile.readText())
    } catch (e: Exception) {
        log(TAG, WARN) { "Failed to read trigger file: $e" }
        null
    }

    private fun writeTriggerFile(sessionDir: File, startTime: Long) {
        try {
            triggerFile.writeText("${sessionDir.absolutePath}\n$startTime")
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to write trigger file: $e" }
            try {
                triggerFile.createNewFile()
            } catch (e2: Exception) {
                log(TAG, ERROR) { "Failed to create trigger file fallback: $e2" }
            }
        }
    }

    companion object {
        internal val TAG = logTag("Debug", "Log", "Recorder", "Module")
        private const val FORCE_FILE = "capod_force_debug_run"

        /**
         * Duration heuristic for "did you forget to reproduce the issue?". A recording stopped
         * this quickly usually contains nothing but the recorder starting and stopping, which
         * costs a support round-trip to re-request.
         *
         * It stays a prompt because short recordings can be perfectly valid: a crash is logged
         * and flushed immediately, so the reproduction is already on disk. "Stop anyway" works —
         * the [StopResult.TooShort] consumers stop via [stopRecorder], which has no duration check.
         */
        private const val MIN_RECORDING_MS = 10_000L

        // Budget for the header's diagnostics read.
        private const val HEADER_READ_TIMEOUT_MS = 5_000L

        @VisibleForTesting
        internal fun parseTriggerContent(
            content: String,
            now: Long = SystemTimeSource.currentTimeMillis(),
        ): Pair<File, Long>? {
            val trimmed = content.trim()
            if (trimmed.isEmpty()) return null

            val lines = trimmed.lines()
            if (lines.size < 2) {
                log(TAG, WARN) { "Trigger file has unexpected format: $trimmed" }
                return null
            }

            val dir = File(lines[0])
            val timestamp = lines[1].toLongOrNull()

            if (timestamp == null || timestamp !in 1..(now + 60_000L)) {
                log(TAG, WARN) { "Trigger file has invalid timestamp: ts=$timestamp" }
                return null
            }

            if (!dir.exists()) {
                log(TAG, WARN) { "Trigger file references non-existent dir: ${lines[0]}" }
                return null
            }

            return dir to timestamp
        }
    }
}
