package eu.darken.capod.common.debug.recording.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.BuildConfigWrap
import eu.darken.capod.common.InstallId
import eu.darken.capod.common.compression.Zipper
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecorderModule @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val installId: InstallId,
    private val debugLogZipper: DebugLogZipper,
) {

    private val fsMutex = Mutex()
    @Volatile private var compressingSessionId: String? = null

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
                        val sessionId = deriveSessionId(logDir)

                        compressingSessionId = sessionId
                        sessionsState.updateBlocking {
                            scanSessions(activeDir = null, recordingStartedAt = 0L)
                        }

                        if (showResultUi) {
                            val intent = RecorderActivity.getLaunchIntent(context, sessionId, logDir.path).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }

                        appScope.launch(dispatcherProvider.IO) {
                            autoCompress(sessionId)
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

    private fun getLogDirectories(): List<File> = listOfNotNull(
        try {
            context.getExternalFilesDir(null)?.let { File(it, "debug/logs") }
        } catch (e: Exception) {
            null
        },
        File(context.cacheDir, "debug/logs"),
    )

    private val sessionsState = DynamicStateFlow(TAG, appScope + dispatcherProvider.IO) {
        val recState = internalState.value()
        scanSessions(activeDir = recState.currentLogDir, recordingStartedAt = recState.recordingStartedAt)
    }
    val sessions: Flow<List<DebugSession>> = sessionsState.flow

    private fun deriveSessionId(file: File): String {
        val prefix = if (file.absolutePath.contains("/cache/")) "cache:" else "ext:"
        return prefix + file.name.removeSuffix(".zip")
    }

    private fun parseCreatedAt(dirName: String, fallback: Long): Long {
        // Pattern: capod_{version}_{timestamp}_{installId}
        // Parse from right: installId is last segment, timestamp is second-to-last
        val parts = dirName.removeSuffix(".zip").split("_")
        if (parts.size >= 4) {
            // timestamp is second-to-last part
            val timestamp = parts[parts.size - 2].toLongOrNull()
            if (timestamp != null && timestamp > 1_000_000_000_000L) return timestamp
        }
        return fallback
    }

    private fun scanSessions(
        activeDir: File? = null,
        recordingStartedAt: Long = 0L,
    ): List<DebugSession> {

        data class RawEntry(val dir: File?, val zip: File?, val parentDir: File)

        val entriesByBaseName = mutableMapOf<String, RawEntry>()

        for (logParent in getLogDirectories()) {
            if (!logParent.exists()) continue
            val files = logParent.listFiles() ?: continue
            for (file in files) {
                val baseName = file.name.removeSuffix(".zip")
                val key = logParent.absolutePath + "/" + baseName
                val existing = entriesByBaseName[key]
                if (file.isDirectory) {
                    entriesByBaseName[key] = (existing ?: RawEntry(null, null, logParent)).copy(dir = file)
                } else if (file.isFile && file.extension == "zip") {
                    entriesByBaseName[key] = (existing ?: RawEntry(null, null, logParent)).copy(zip = file)
                }
            }
        }

        return entriesByBaseName.map { (key, raw) ->
            val baseName = key.substringAfterLast("/")
            val prefix = if (key.contains("/cache/")) "cache:" else "ext:"
            val id = prefix + baseName
            val fallbackTime = (raw.dir ?: raw.zip)?.lastModified() ?: 0L
            val createdAt = parseCreatedAt(baseName, fallbackTime)

            val dir = raw.dir
            val zip = raw.zip

            // Is this the currently recording session?
            if (dir != null && dir == activeDir) {
                val dirSize = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                return@map DebugSession.Recording(
                    id = id,
                    displayName = baseName,
                    createdAt = createdAt,
                    diskSize = dirSize,
                    path = dir,
                    startedAt = recordingStartedAt,
                )
            }

            // Check dir validity
            if (dir != null) {
                val coreLog = File(dir, "core.log")
                val dirSize = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

                if (!coreLog.exists()) {
                    // Missing core.log — check if sibling zip is valid
                    if (zip != null && zip.exists() && zip.length() > 0) {
                        return@map DebugSession.Ready(
                            id = id,
                            displayName = baseName,
                            createdAt = createdAt,
                            diskSize = zip.length(),
                            logDir = null,
                        )
                    }
                    return@map DebugSession.Failed(
                        id = id,
                        displayName = baseName,
                        createdAt = createdAt,
                        diskSize = dirSize,
                        path = dir,
                        reason = DebugSession.Failed.Reason.MISSING_LOG,
                    )
                }

                if (coreLog.length() == 0L) {
                    if (zip != null && zip.exists() && zip.length() > 0) {
                        return@map DebugSession.Ready(
                            id = id,
                            displayName = baseName,
                            createdAt = createdAt,
                            diskSize = zip.length(),
                            logDir = null,
                        )
                    }
                    return@map DebugSession.Failed(
                        id = id,
                        displayName = baseName,
                        createdAt = createdAt,
                        diskSize = dirSize,
                        path = dir,
                        reason = DebugSession.Failed.Reason.EMPTY_LOG,
                    )
                }

                // Valid dir with core.log
                val zipSize = if (zip != null && zip.exists()) zip.length() else 0L
                val totalDiskSize = dirSize + zipSize
                return@map DebugSession.Ready(
                    id = id,
                    displayName = baseName,
                    createdAt = createdAt,
                    diskSize = totalDiskSize,
                    logDir = dir,
                )
            }

            // Standalone zip only
            if (zip != null && zip.exists()) {
                if (zip.length() == 0L) {
                    return@map DebugSession.Failed(
                        id = id,
                        displayName = baseName,
                        createdAt = createdAt,
                        diskSize = 0L,
                        path = zip,
                        reason = DebugSession.Failed.Reason.CORRUPT_ZIP,
                    )
                }
                return@map DebugSession.Ready(
                    id = id,
                    displayName = baseName,
                    createdAt = createdAt,
                    diskSize = zip.length(),
                    logDir = null,
                )
            }

            // Should not happen, but handle defensively
            DebugSession.Failed(
                id = id,
                displayName = baseName,
                createdAt = createdAt,
                diskSize = 0L,
                path = File(key),
                reason = DebugSession.Failed.Reason.MISSING_LOG,
            )
        }.map { session ->
            if (session is DebugSession.Ready && session.id == compressingSessionId) {
                DebugSession.Compressing(
                    id = session.id,
                    displayName = session.displayName,
                    createdAt = session.createdAt,
                    diskSize = session.diskSize,
                    path = session.logDir ?: File(""),
                )
            } else {
                session
            }
        }.sortedByDescending { it.createdAt }
    }

    suspend fun refreshSessions() {
        val recState = internalState.value()
        sessionsState.updateBlocking {
            scanSessions(activeDir = recState.currentLogDir, recordingStartedAt = recState.recordingStartedAt)
        }
    }

    private fun findSessionFiles(sessionId: String): Pair<File?, File?> {
        val baseName = sessionId.removePrefix("ext:").removePrefix("cache:")
        for (logParent in getLogDirectories()) {
            val dir = File(logParent, baseName)
            val zip = File(logParent, "$baseName.zip")
            val idPrefix = if (logParent.absolutePath.contains("/cache/")) "cache:" else "ext:"
            if (idPrefix + baseName == sessionId) {
                val dirExists = dir.exists() && dir.isDirectory
                val zipExists = zip.exists() && zip.isFile
                if (dirExists || zipExists) {
                    return Pair(if (dirExists) dir else null, if (zipExists) zip else null)
                }
            }
        }
        return Pair(null, null)
    }

    private suspend fun autoCompress(sessionId: String) {
        try {
            zipSession(sessionId)
        } catch (e: Exception) {
            log(TAG, ERROR) { "Auto-compress failed for $sessionId: $e" }
        } finally {
            compressingSessionId = null
            refreshSessions()
        }
    }

    suspend fun zipSession(sessionId: String): File = fsMutex.withLock {
        val (dir, existingZip) = findSessionFiles(sessionId)

        // If zip already exists and is fresh, just return it
        if (existingZip != null && existingZip.length() > 0) {
            if (dir == null || existingZip.lastModified() >= dir.lastModified()) {
                return@withLock existingZip
            }
        }

        requireNotNull(dir) { "No log directory found for session $sessionId" }

        val logFiles = dir.listFiles()?.toList()
            ?: throw IllegalStateException("No log files in $dir")

        val targetZip = File(dir.parentFile, "${dir.name}.zip")
        val tempZip = File(dir.parentFile, "${dir.name}.zip.tmp")
        try {
            Zipper().zip(logFiles.map { it.path }.toTypedArray(), tempZip.path)
            tempZip.renameTo(targetZip)
        } catch (e: Exception) {
            tempZip.delete()
            throw e
        }

        targetZip
    }

    suspend fun deleteSession(sessionId: String) = fsMutex.withLock {
        val currentRecording = sessions.first().filterIsInstance<DebugSession.Recording>()
            .firstOrNull { it.id == sessionId }
        require(currentRecording == null) { "Cannot delete an active recording session" }

        val (dir, zip) = findSessionFiles(sessionId)
        dir?.deleteRecursively()
        zip?.delete()

        log(TAG) { "Deleted session: $sessionId" }
        val recState = internalState.value()
        sessionsState.updateBlocking {
            scanSessions(activeDir = recState.currentLogDir, recordingStartedAt = recState.recordingStartedAt)
        }
    }

    suspend fun getZipUri(sessionId: String): Uri {
        val zipFile = zipSession(sessionId)
        return debugLogZipper.getUriForZip(zipFile)
    }

    suspend fun deleteAllLogs() = fsMutex.withLock {
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
        val recState = internalState.value()
        sessionsState.updateBlocking {
            scanSessions(activeDir = recState.currentLogDir, recordingStartedAt = recState.recordingStartedAt)
        }
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
