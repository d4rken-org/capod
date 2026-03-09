package eu.darken.capod.common.debug.recording.core

import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.annotation.VisibleForTesting
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.BuildConfigWrap
import eu.darken.capod.common.InstallId
import eu.darken.capod.common.coroutine.AppScope
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.logging.Logging.Priority.ERROR
import eu.darken.capod.common.debug.logging.Logging.Priority.INFO
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.DynamicStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.plus
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecorderModule @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val installId: InstallId,
) {

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
                        val newRecorder = Recorder()
                        newRecorder.start(logFile)

                        if (!isResume) {
                            val startTime = System.currentTimeMillis()
                            writeTriggerFile(sessionDir, startTime)
                            log(TAG, INFO) { "Build.Fingerprint: ${Build.FINGERPRINT}" }
                            log(TAG, INFO) { "BuildConfig.Versions: ${BuildConfigWrap.VERSION_DESCRIPTION}" }

                            this@RecorderModule.currentLogDir = sessionDir

                            copy(
                                recorder = newRecorder,
                                currentLogDir = sessionDir,
                                recordingStartedAt = startTime,
                                persistedLogDir = null,
                            )
                        } else {
                            log(TAG, INFO) { "Build.Fingerprint: ${Build.FINGERPRINT}" }
                            log(TAG, INFO) { "BuildConfig.Versions: ${BuildConfigWrap.VERSION_DESCRIPTION}" }

                            this@RecorderModule.currentLogDir = sessionDir

                            copy(
                                recorder = newRecorder,
                                currentLogDir = sessionDir,
                                recordingStartedAt = if (recordingStartedAt > 0L) recordingStartedAt else System.currentTimeMillis(),
                                persistedLogDir = null,
                            )
                        }
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
                        )
                    } else {
                        this
                    }
                }
            }
            .launchIn(appScope)
    }

    private fun createSessionDir(): File {
        val timestamp = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC)
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
        val elapsed = System.currentTimeMillis() - currentState.recordingStartedAt
        if (elapsed < MIN_RECORDING_MS) return StopResult.TooShort

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
        private const val MIN_RECORDING_MS = 5_000L

        @VisibleForTesting
        internal fun parseTriggerContent(
            content: String,
            now: Long = System.currentTimeMillis(),
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
