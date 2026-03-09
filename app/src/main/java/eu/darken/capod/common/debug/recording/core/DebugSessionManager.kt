package eu.darken.capod.common.debug.recording.core

import android.net.Uri
import androidx.annotation.VisibleForTesting
import eu.darken.capod.common.coroutine.AppScope
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.logging.Logging.Priority.ERROR
import eu.darken.capod.common.debug.logging.Logging.Priority.INFO
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.replayingShare
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DebugSessionManager @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val recorderModule: RecorderModule,
    private val debugLogZipper: DebugLogZipper,
) {

    private val fsMutex = Mutex()
    private val zippingIds = MutableStateFlow<Set<String>>(emptySet())
    private val failedZipIds = MutableStateFlow<Set<String>>(emptySet())
    private val refreshTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val pendingAutoZips: MutableSet<String> = java.util.Collections.synchronizedSet(mutableSetOf())

    val recorderState: Flow<RecorderModule.State> get() = recorderModule.state

    val sessions: Flow<List<DebugSession>> = combine(
        recorderModule.state,
        zippingIds,
        failedZipIds,
        refreshTrigger.onStart { emit(Unit) },
    ) { recorderState, zipping, failedZips, _ ->
        val raw = scanSessions(
            logDirectories = recorderModule.getLogDirectories(),
            activeDir = recorderState.currentLogDir,
            recordingStartedAt = recorderState.recordingStartedAt,
        )
        val overlaid = applyOverlays(raw, zipping, failedZips)

        val orphans = findOrphans(overlaid, zipping)
        if (orphans.isNotEmpty()) {
            orphans.forEach { (id, _) -> pendingAutoZips.add(id) }
            appScope.launch {
                orphans.forEach { (id, dir) ->
                    log(TAG, INFO) { "Orphan session detected, auto-zipping: $id" }
                    zipSessionAsync(id, dir)
                }
            }
        }

        overlaid
    }.replayingShare(appScope)

    private fun applyOverlays(
        sessions: List<DebugSession>,
        zipping: Set<String>,
        failedZips: Set<String>,
    ): List<DebugSession> = sessions.map { session ->
        when {
            session.id in zipping -> {
                val path = (session as? DebugSession.Ready)?.logDir
                if (path == null) log(TAG, WARN) { "No logDir for session in zippingIds: ${session.id}" }
                DebugSession.Compressing(
                    id = session.id,
                    displayName = session.displayName,
                    createdAt = session.createdAt,
                    diskSize = session.diskSize,
                    path = path ?: File(""),
                )
            }

            session.id in failedZips && session !is DebugSession.Failed -> {
                val path = (session as? DebugSession.Ready)?.logDir
                if (path == null) log(TAG, WARN) { "No logDir for failed-zip session: ${session.id}" }
                DebugSession.Failed(
                    id = session.id,
                    displayName = session.displayName,
                    createdAt = session.createdAt,
                    diskSize = session.diskSize,
                    path = path ?: File(""),
                    reason = DebugSession.Failed.Reason.ZIP_FAILED,
                )
            }

            else -> session
        }
    }

    private fun findOrphans(
        sessions: List<DebugSession>,
        zipping: Set<String>,
    ): List<Pair<String, File>> {
        return sessions.filterIsInstance<DebugSession.Ready>()
            .filter { it.logDir != null && it.id !in zipping && it.id !in pendingAutoZips }
            .filter { it.zipFile == null || it.compressedSize == 0L }
            .map { it.id to it.logDir!! }
    }

    private fun zipSessionAsync(sessionId: String, logDir: File) {
        zippingIds.update { it + sessionId }
        appScope.launch(dispatcherProvider.IO) {
            try {
                fsMutex.withLock {
                    debugLogZipper.zip(logDir)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(TAG, ERROR) { "Zipping failed for $sessionId: $e" }
                failedZipIds.update { it + sessionId }
            } finally {
                pendingAutoZips.remove(sessionId)
                zippingIds.update { it - sessionId }
                refresh()
            }
        }
    }

    suspend fun startRecording(): File = recorderModule.startRecorder()

    suspend fun requestStopRecording(): RecorderModule.StopResult {
        val result = recorderModule.requestStopRecorder()
        if (result is RecorderModule.StopResult.Stopped) {
            zipSessionAsync(result.sessionId, result.logDir)
        }
        return result
    }

    suspend fun forceStopRecording(): RecorderModule.StopResult.Stopped? {
        val logDir = recorderModule.stopRecorder() ?: return null
        val sessionId = deriveSessionId(logDir)
        zipSessionAsync(sessionId, logDir)
        return RecorderModule.StopResult.Stopped(logDir, sessionId)
    }

    fun refresh() {
        refreshTrigger.tryEmit(Unit)
    }

    private fun activeSessionId(): String? = recorderModule.currentLogDir?.let { deriveSessionId(it) }

    suspend fun zipSession(sessionId: String): File = fsMutex.withLock {
        // Do NOT call sessions.first() here — deadlock risk with fsMutex
        require(activeSessionId() != sessionId) { "Cannot zip an active recording session" }

        val (dir, existingZip) = findSessionFiles(sessionId)

        if (existingZip != null && existingZip.length() > 0) {
            if (dir == null || existingZip.lastModified() >= dir.lastModified()) {
                return@withLock existingZip
            }
        }

        requireNotNull(dir) { "No log directory found for session $sessionId" }
        withContext(dispatcherProvider.IO) {
            debugLogZipper.zip(dir)
        }
    }

    suspend fun getZipUri(sessionId: String): Uri {
        val zipFile = zipSession(sessionId)
        return debugLogZipper.getUriForZip(zipFile)
    }

    suspend fun deleteSession(sessionId: String) = fsMutex.withLock {
        // Do NOT call sessions.first() here — deadlock risk with fsMutex
        require(activeSessionId() != sessionId) { "Cannot delete an active recording session" }
        require(sessionId !in zippingIds.value) { "Cannot delete a session that is being compressed" }

        withContext(dispatcherProvider.IO) {
            val (dir, zip) = findSessionFiles(sessionId)
            if (dir?.deleteRecursively() == false) {
                log(TAG, WARN) { "Failed to fully delete session dir: ${dir.path}" }
            }
            if (zip?.delete() == false) {
                log(TAG, WARN) { "Failed to delete session zip: ${zip.path}" }
            }
        }
        failedZipIds.update { it - sessionId }

        log(TAG) { "Deleted session: $sessionId" }
        refresh()
    }

    suspend fun deleteAllSessions() = fsMutex.withLock {
        val activeDir = recorderModule.currentLogDir
        val currentlyZipping = zippingIds.value
        withContext(dispatcherProvider.IO) {
            for (dir in recorderModule.getLogDirectories()) {
                if (!dir.exists()) continue
                for (entry in dir.listFiles() ?: emptyArray()) {
                    if (entry == activeDir) {
                        log(TAG) { "Skipping active session dir: $entry" }
                        continue
                    }
                    val entryId = deriveSessionId(entry)
                    if (entryId in currentlyZipping) {
                        log(TAG) { "Skipping zipping session: $entry" }
                        continue
                    }
                    val deleted = if (entry.isDirectory) entry.deleteRecursively() else entry.delete()
                    if (!deleted) log(TAG, WARN) { "Failed to delete: ${entry.path}" }
                }
            }
        }
        failedZipIds.update { emptySet() }
        log(TAG) { "All stored logs deleted" }
        refresh()
    }

    private fun findSessionFiles(sessionId: String): Pair<File?, File?> {
        val baseName = sessionId.removePrefix("ext:").removePrefix("cache:")
        for (logParent in recorderModule.getLogDirectories()) {
            val dir = File(logParent, baseName)
            val zip = File(logParent, "$baseName.zip")
            val idPrefix = if (logParent.absolutePath.contains("/cache/debug/logs")) "cache:" else "ext:"
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

    companion object {
        private val TAG = logTag("Debug", "Log", "Session", "Manager")

        @VisibleForTesting
        internal fun deriveSessionId(file: File): String {
            val prefix = if (file.absolutePath.contains("/cache/debug/logs")) "cache:" else "ext:"
            return prefix + file.name.removeSuffix(".zip")
        }

        @VisibleForTesting
        internal fun parseCreatedAt(file: File): Instant = try {
            val attrs = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
            attrs.creationTime().toInstant()
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to read creation time for ${file.name}: ${e.message}" }
            Instant.ofEpochMilli(file.lastModified())
        }

        private fun computeDiskSize(file: File): Long {
            if (!file.exists()) return 0L
            return if (file.isDirectory) {
                file.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            } else {
                file.length()
            }
        }

        @VisibleForTesting
        internal fun scanSessions(
            logDirectories: List<File>,
            activeDir: File? = null,
            recordingStartedAt: Long = 0L,
        ): List<DebugSession> {

            data class RawEntry(val dir: File?, val zip: File?, val parentDir: File)

            val entriesByBaseName = mutableMapOf<String, RawEntry>()

            for (logParent in logDirectories) {
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
                val prefix = if (key.contains("/cache/debug/logs")) "cache:" else "ext:"
                val id = prefix + baseName
                val displayName = baseName

                val dir = raw.dir
                val zip = raw.zip
                val referenceFile = dir ?: zip ?: File(raw.parentDir, baseName)
                val createdAt = parseCreatedAt(referenceFile)

                when {
                    dir != null && dir == activeDir -> {
                        val dirSize = computeDiskSize(dir)
                        DebugSession.Recording(
                            id = id,
                            displayName = displayName,
                            createdAt = createdAt,
                            diskSize = dirSize,
                            path = dir,
                            startedAt = recordingStartedAt,
                        )
                    }

                    dir != null && zip != null -> classifyWithZip(id, displayName, createdAt, dir, zip)

                    dir != null -> classifyOrphan(id, displayName, createdAt, dir)

                    zip != null && zip.exists() -> {
                        if (zip.length() == 0L) {
                            DebugSession.Failed(
                                id = id,
                                displayName = displayName,
                                createdAt = createdAt,
                                diskSize = 0L,
                                path = zip,
                                reason = DebugSession.Failed.Reason.CORRUPT_ZIP,
                            )
                        } else {
                            DebugSession.Ready(
                                id = id,
                                displayName = displayName,
                                createdAt = createdAt,
                                diskSize = zip.length(),
                                logDir = null,
                                zipFile = zip,
                                compressedSize = zip.length(),
                            )
                        }
                    }

                    else -> DebugSession.Failed(
                        id = id,
                        displayName = displayName,
                        createdAt = createdAt,
                        diskSize = 0L,
                        path = File(raw.parentDir, baseName),
                        reason = DebugSession.Failed.Reason.MISSING_LOG,
                    )
                }
            }.sortedWith(compareByDescending<DebugSession> { it.createdAt }.thenBy { it.id })
        }

        private fun classifyWithZip(
            id: String,
            displayName: String,
            createdAt: Instant,
            dir: File,
            zip: File,
        ): DebugSession {
            val coreLog = File(dir, "core.log")
            val dirSize = computeDiskSize(dir)
            val zipValid = zip.exists() && zip.length() > 0
            val totalDiskSize = dirSize + (if (zipValid) zip.length() else 0L)

            return when {
                !coreLog.exists() && zipValid -> DebugSession.Ready(
                    id = id, displayName = displayName, createdAt = createdAt,
                    diskSize = zip.length(), logDir = null, zipFile = zip, compressedSize = zip.length(),
                )
                !coreLog.exists() -> DebugSession.Failed(
                    id = id, displayName = displayName, createdAt = createdAt,
                    diskSize = dirSize, path = dir, reason = DebugSession.Failed.Reason.MISSING_LOG,
                )
                coreLog.length() == 0L && zipValid -> DebugSession.Ready(
                    id = id, displayName = displayName, createdAt = createdAt,
                    diskSize = zip.length(), logDir = null, zipFile = zip, compressedSize = zip.length(),
                )
                coreLog.length() == 0L -> DebugSession.Failed(
                    id = id, displayName = displayName, createdAt = createdAt,
                    diskSize = dirSize, path = dir, reason = DebugSession.Failed.Reason.EMPTY_LOG,
                )
                else -> DebugSession.Ready(
                    id = id, displayName = displayName, createdAt = createdAt,
                    diskSize = totalDiskSize, logDir = dir, zipFile = if (zipValid) zip else null,
                    compressedSize = if (zipValid) zip.length() else 0L,
                )
            }
        }

        private fun classifyOrphan(
            id: String,
            displayName: String,
            createdAt: Instant,
            dir: File,
        ): DebugSession {
            val coreLog = File(dir, "core.log")
            val dirSize = computeDiskSize(dir)

            return when {
                !coreLog.exists() -> DebugSession.Failed(
                    id = id, displayName = displayName, createdAt = createdAt,
                    diskSize = dirSize, path = dir, reason = DebugSession.Failed.Reason.MISSING_LOG,
                )
                coreLog.length() == 0L -> DebugSession.Failed(
                    id = id, displayName = displayName, createdAt = createdAt,
                    diskSize = dirSize, path = dir, reason = DebugSession.Failed.Reason.EMPTY_LOG,
                )
                else -> DebugSession.Ready(
                    id = id, displayName = displayName, createdAt = createdAt,
                    diskSize = dirSize, logDir = dir, zipFile = null, compressedSize = 0L,
                )
            }
        }
    }
}
