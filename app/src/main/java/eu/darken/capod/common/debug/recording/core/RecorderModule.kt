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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.plus
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
                        val isResume = persistedLogDir != null && persistedLogDir.exists()
                        val sessionDir = if (isResume) {
                            log(TAG, INFO) { "Resuming recording into existing session: $persistedLogDir" }
                            persistedLogDir
                        } else {
                            createSessionDir()
                        }
                        val logFile = File(sessionDir, "core.log")
                        val newRecorder = Recorder(timeSource)
                        newRecorder.start(logFile)

                        val startTime = when {
                            !isResume -> timeSource.currentTimeMillis()
                            recordingStartedAt > 0L -> recordingStartedAt
                            else -> timeSource.currentTimeMillis()
                        }

                        try {
                            if (!isResume) writeTriggerFile(sessionDir, startTime)

                            logRecordingHeader()
                        } catch (e: Exception) {
                            // The recorder is already live but not yet committed to the state: an exception
                            // escaping the header would abandon it where stopRecorder() can't reach it.
                            withContext(NonCancellable) {
                                try {
                                    newRecorder.stop()
                                } catch (stopError: Exception) {
                                    e.addSuppressed(stopError)
                                }
                                this@RecorderModule.currentLogDir = null
                            }
                            throw e
                        }

                        this@RecorderModule.currentLogDir = sessionDir

                        copy(
                            recorder = newRecorder,
                            currentLogDir = sessionDir,
                            recordingStartedAt = startTime,
                            recordingStartedAtMonotonic = if (isResume) null else timeSource.elapsedRealtime(),
                            persistedLogDir = null,
                        )
                    } else if (!shouldRecord && isRecording) {
                        requireNotNull(recorder) { "Recorder is null despite isRecording" }.stop()

                        if (triggerFile.exists() && !triggerFile.delete()) {
                            log(TAG, ERROR) { "Failed to delete trigger file" }
                        }

                        this@RecorderModule.currentLogDir = null

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
    // every read here is diagnostics-only and must never propagate: a failure would abort the state
    // update and leave a RUNNING recorder that the module no longer knows about.
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

    private fun createSessionDir(): File {
        val timestamp = timeSource.now().atZone(java.time.ZoneOffset.UTC)
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))
        val installIdPrefix = installId.id.take(8)
        val dirName = "capod_${BuildConfigWrap.VERSION_NAME}_${timestamp}_$installIdPrefix"

        val primaryParent = try {
            val dir = File(context.getExternalFilesDir(null), "debug/logs")
            dir.mkdirs()
            if (dir.canWrite()) dir else null
        } catch (e: Exception) {
            log(TAG, WARN) { "External files dir unavailable: $e" }
            null
        }

        val parent = primaryParent ?: File(context.cacheDir, "debug/logs").also { it.mkdirs() }
        val sessionDir = File(parent, dirName)
        sessionDir.mkdirs()

        log(TAG) { "Created session dir: $sessionDir" }
        return sessionDir
    }

    internal fun getLogDirectories(): List<File> = listOfNotNull(
        try {
            context.getExternalFilesDir(null)?.let { File(it, "debug/logs") }
        } catch (e: Exception) {
            null
        },
        File(context.cacheDir, "debug/logs"),
    )

    suspend fun startRecorder(): File {
        internalState.updateBlocking {
            copy(shouldRecord = true)
        }
        val state = internalState.flow.filter { it.isRecording }.first()
        return requireNotNull(state.currentLogDir) { "Recording state has no logDir" }
    }

    suspend fun stopRecorder(): File? {
        val currentDir = internalState.value().currentLogDir ?: return null
        internalState.updateBlocking {
            copy(shouldRecord = false)
        }
        internalState.flow.filter { !it.isRecording }.first()
        return currentDir
    }

    suspend fun requestStopRecorder(): StopResult {
        val currentState = internalState.value()
        if (!currentState.isRecording) return StopResult.NotRecording

        val logDir = currentState.currentLogDir ?: return StopResult.NotRecording
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
        if (elapsed in 0 until MIN_RECORDING_MS) return StopResult.TooShort

        stopRecorder()
        val sessionId = DebugSessionManager.deriveSessionId(logDir)
        return StopResult.Stopped(logDir, sessionId)
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
    ) {
        val isRecording: Boolean
            get() = recorder != null

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
