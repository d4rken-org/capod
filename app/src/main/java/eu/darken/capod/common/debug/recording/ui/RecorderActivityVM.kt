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
import eu.darken.capod.common.flow.onError
import eu.darken.capod.common.livedata.SingleLiveEvent
import eu.darken.capod.common.uix.ViewModel3
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
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
) : ViewModel3(dispatcherProvider) {

    private val recordedDirPath = handle.get<String>(RecorderActivity.RECORD_PATH)
    private val logDir = recordedDirPath?.let { File(it) }

    private val stater = DynamicStateFlow(TAG, vmScope + dispatcherProvider.IO) {
        if (logDir == null || !logDir.exists()) {
            return@DynamicStateFlow State(logDir = null)
        }

        val files = logDir.listFiles()?.toList() ?: emptyList()
        val entries = files.map { LogFileAdapter.Item(it, it.length()) }
        val totalSize = entries.sumOf { it.size }

        val compressedSize = try {
            val zipFile = File(logDir.parentFile, "${logDir.name}.zip")
            debugLogZipper.zipAndGetUri(logDir)
            zipFile.length()
        } catch (e: Exception) {
            log(TAG) { "Failed to zip: $e" }
            -1L
        }

        State(
            logDir = logDir,
            logEntries = entries,
            totalSize = totalSize,
            compressedSize = compressedSize,
            isWorking = false,
        )
    }
    val state = stater.asLiveData2()

    val shareEvent = SingleLiveEvent<Intent>()
    val finishEvent = SingleLiveEvent<Unit>()

    init {
        stater.flow
            .onEach { log(TAG) { "State: $it" } }
            .onError { errorEvents.postValue(it) }
            .launchInViewModel()
    }

    fun share() = launch {
        val currentState = stater.flow.first()
        val dir = currentState.logDir ?: return@launch

        stater.updateBlocking { copy(isWorking = true) }

        try {
            val uri = debugLogZipper.zipAndGetUri(dir)

            val intent = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                type = "application/zip"
                addCategory(Intent.CATEGORY_DEFAULT)
                putExtra(Intent.EXTRA_SUBJECT, "CAPod DebugLog - ${dir.name}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooserIntent = Intent.createChooser(intent, context.getString(R.string.support_debuglog_label))
            shareEvent.postValue(chooserIntent)
        } finally {
            stater.updateBlocking { copy(isWorking = false) }
        }
    }

    fun keep() {
        finishEvent.postValue(Unit)
    }

    fun discard() = launch {
        val currentState = stater.flow.first()
        val dir = currentState.logDir ?: return@launch

        dir.deleteRecursively()
        val zipFile = File(dir.parentFile, "${dir.name}.zip")
        if (zipFile.exists()) zipFile.delete()

        finishEvent.postValue(Unit)
    }

    fun goPrivacyPolicy() {
        webpageTool.open(PrivacyPolicy.URL)
    }

    data class State(
        val logDir: File? = null,
        val logEntries: List<LogFileAdapter.Item> = emptyList(),
        val totalSize: Long = 0L,
        val compressedSize: Long = -1L,
        val isWorking: Boolean = true,
    )

    companion object {
        private val TAG = logTag("Debug", "Recorder", "VM")
    }
}
