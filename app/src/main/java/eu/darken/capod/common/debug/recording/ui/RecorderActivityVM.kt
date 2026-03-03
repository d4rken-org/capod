package eu.darken.capod.common.debug.recording.ui

import android.content.Context
import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.R
import eu.darken.capod.common.PrivacyPolicy
import eu.darken.capod.common.WebpageTool
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.debug.recording.core.DebugLogZipper
import eu.darken.capod.common.flow.DynamicStateFlow
import eu.darken.capod.common.flow.SingleEventFlow
import eu.darken.capod.common.uix.ViewModel2
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.plus
import java.io.File
import javax.inject.Inject

@HiltViewModel
class RecorderActivityVM @Inject constructor(
    handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val context: Context,
    private val debugLogZipper: DebugLogZipper,
    private val webpageTool: WebpageTool,
) : ViewModel2(dispatcherProvider) {

    data class LogEntry(
        val file: File,
        val size: Long,
    )

    data class State(
        val logDir: File? = null,
        val logEntries: List<LogEntry> = emptyList(),
        val totalSize: Long = 0L,
        val compressedSize: Long = -1L,
        val recordingDurationSecs: Long = 0L,
        val isWorking: Boolean = true,
    )

    sealed interface Event {
        data class ShareIntent(val intent: Intent) : Event
        data object Finish : Event
    }

    private val recordedDirPath = handle.get<String>(RecorderActivity.RECORD_PATH)
    private val logDir = recordedDirPath?.let { File(it) }

    private val stater = DynamicStateFlow(TAG, vmScope + dispatcherProvider.IO) {
        if (logDir == null || !logDir.exists()) {
            return@DynamicStateFlow State(logDir = null)
        }

        val files = logDir.listFiles()?.toList() ?: emptyList()
        val entries = files.map { LogEntry(it, it.length()) }
        val totalSize = entries.sumOf { it.size }

        val compressedSize = try {
            debugLogZipper.zipAndGetUri(logDir)
            File(logDir.parentFile, "${logDir.name}.zip").length()
        } catch (e: Exception) {
            log(TAG) { "Failed to zip: $e" }
            -1L
        }

        val dirCreated = logDir.lastModified()
        val latestFileModified = files.maxOfOrNull { it.lastModified() } ?: dirCreated
        val durationSecs = ((latestFileModified - dirCreated) / 1000).coerceAtLeast(0)

        State(
            logDir = logDir,
            logEntries = entries,
            totalSize = totalSize,
            compressedSize = compressedSize,
            recordingDurationSecs = durationSecs,
            isWorking = false,
        )
    }
    val state = stater.flow

    val events = SingleEventFlow<Event>()

    fun share() = launch {
        val currentState = stater.flow.first()
        val dir = currentState.logDir ?: return@launch

        stater.updateBlocking { copy(isWorking = true) }

        try {
            val zipFile = File(dir.parentFile, "${dir.name}.zip")
            val uri = if (zipFile.exists()) {
                debugLogZipper.getUriForZip(zipFile)
            } else {
                debugLogZipper.zipAndGetUri(dir)
            }

            val intent = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                type = "application/zip"
                addCategory(Intent.CATEGORY_DEFAULT)
                putExtra(Intent.EXTRA_SUBJECT, "CAPod DebugLog - ${dir.name}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooserIntent = Intent.createChooser(intent, context.getString(R.string.support_debuglog_label))
            events.tryEmit(Event.ShareIntent(chooserIntent))
        } finally {
            stater.updateBlocking { copy(isWorking = false) }
        }
    }

    fun keep() {
        events.tryEmit(Event.Finish)
    }

    fun discard() = launch {
        val currentState = stater.flow.first()
        val dir = currentState.logDir ?: return@launch

        dir.deleteRecursively()
        val zipFile = File(dir.parentFile, "${dir.name}.zip")
        if (zipFile.exists()) zipFile.delete()

        events.tryEmit(Event.Finish)
    }

    fun goPrivacyPolicy() {
        webpageTool.open(PrivacyPolicy.URL)
    }

    companion object {
        private val TAG = logTag("Debug", "Recorder", "VM")
    }
}
