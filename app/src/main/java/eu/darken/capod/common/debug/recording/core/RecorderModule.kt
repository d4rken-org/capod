package eu.darken.capod.common.debug.recording.core

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
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
import eu.darken.capod.common.debug.recording.ui.RecorderActivity
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
        State(shouldRecord = triggerFileExists)
    }
    val state: Flow<State> = internalState.flow

    init {
        internalState.flow
            .onEach {
                log(TAG) { "New Recorder state: $it" }

                internalState.updateBlocking {
                    if (!isRecording && shouldRecord) {
                        val sessionDir = createSessionDir()
                        val logFile = File(sessionDir, "core.log")
                        val newRecorder = Recorder()
                        newRecorder.start(logFile)
                        triggerFile.createNewFile()

                        log(TAG, INFO) { "Build.Fingerprint: ${Build.FINGERPRINT}" }
                        log(TAG, INFO) { "BuildConfig.Versions: ${BuildConfigWrap.VERSION_DESCRIPTION}" }

                        copy(
                            recorder = newRecorder,
                            currentLogDir = sessionDir,
                            recordingStartedAt = System.currentTimeMillis(),
                        )
                    } else if (!shouldRecord && isRecording) {
                        recorder!!.stop()

                        if (triggerFile.exists() && !triggerFile.delete()) {
                            log(TAG, ERROR) { "Failed to delete trigger file" }
                        }

                        val logDir = currentLogDir!!

                        if (showResultUi) {
                            val intent = RecorderActivity.getLaunchIntent(context, logDir.path).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }

                        copy(
                            recorder = null,
                            currentLogDir = null,
                            lastLogDir = logDir,
                            showResultUi = true,
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
        val timestamp = System.currentTimeMillis()
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

    fun getLogDirectories(): List<File> = listOfNotNull(
        try {
            context.getExternalFilesDir(null)?.let { File(it, "debug/logs") }
        } catch (e: Exception) {
            null
        },
        File(context.cacheDir, "debug/logs"),
    )

    fun getLogSessionCount(): Int {
        return getLogDirectories().sumOf { dir ->
            if (!dir.exists()) return@sumOf 0
            dir.listFiles()?.count { it.isDirectory || (it.isFile && it.extension == "zip") } ?: 0
        }
    }

    fun getLogFolderSize(): Long {
        return getLogDirectories().sumOf { dir ->
            if (!dir.exists()) return@sumOf 0L
            dir.listFiles()?.sumOf { entry ->
                if (entry.isDirectory) {
                    entry.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                } else {
                    entry.length()
                }
            } ?: 0L
        }
    }

    suspend fun deleteAllLogs() {
        val activeDir = internalState.value().currentLogDir
        getLogDirectories().forEach { dir ->
            if (!dir.exists()) return@forEach
            dir.listFiles()?.forEach { entry ->
                if (entry == activeDir) {
                    log(TAG) { "Skipping active session dir: $entry" }
                    return@forEach
                }
                if (entry.isDirectory) {
                    entry.deleteRecursively()
                } else {
                    entry.delete()
                }
            }
        }
        log(TAG) { "All stored logs deleted" }
    }

    suspend fun startRecorder(): File {
        internalState.updateBlocking {
            copy(shouldRecord = true)
        }
        return internalState.flow.filter { it.isRecording }.first().currentLogDir!!
    }

    suspend fun stopRecorder(showResultUi: Boolean = true): File? {
        val currentDir = internalState.value().currentLogDir ?: return null
        internalState.updateBlocking {
            copy(shouldRecord = false, showResultUi = showResultUi)
        }
        internalState.flow.filter { !it.isRecording }.first()
        return currentDir
    }

    data class State(
        val shouldRecord: Boolean = false,
        internal val recorder: Recorder? = null,
        val currentLogDir: File? = null,
        val lastLogDir: File? = null,
        val recordingStartedAt: Long = 0L,
        internal val showResultUi: Boolean = true,
    ) {
        val isRecording: Boolean
            get() = recorder != null

        val currentLogPath: File?
            get() = recorder?.path
    }

    companion object {
        internal val TAG = logTag("Debug", "Log", "Recorder", "Module")
        private const val FORCE_FILE = "capod_force_debug_run"
    }
}
