package eu.darken.capod.main.ui.settings.support.contactform

import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.BuildConfigWrap
import eu.darken.capod.common.EmailTool
import eu.darken.capod.common.SupportLinks
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.debug.recording.core.DebugLogZipper
import eu.darken.capod.common.debug.recording.core.RecorderModule
import eu.darken.capod.common.flow.DynamicStateFlow
import eu.darken.capod.common.flow.SingleEventFlow
import eu.darken.capod.common.uix.ViewModel4
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ContactFormViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val context: Context,
    private val recorderModule: RecorderModule,
    private val debugLogZipper: DebugLogZipper,
    private val emailTool: EmailTool,
) : ViewModel4(dispatcherProvider) {

    enum class Category { QUESTION, FEATURE, BUG }

    data class LogSessionItem(
        val path: File,
        val size: Long,
        val lastModified: Long,
    )

    data class State(
        val category: Category = Category.QUESTION,
        val description: String = "",
        val expectedBehavior: String = "",
        val isSending: Boolean = false,
        val isRecording: Boolean = false,
        val recordingStartedAt: Long = 0L,
        val sessions: List<LogSessionItem> = emptyList(),
        val selectedSessionPath: File? = null,
    ) {
        val isBug: Boolean get() = category == Category.BUG

        val descriptionWords: Int
            get() = description.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }.size

        val expectedWords: Int
            get() = expectedBehavior.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }.size

        val canSend: Boolean
            get() = descriptionWords >= 20
                    && (!isBug || expectedWords >= 10)
                    && !isSending
                    && !isRecording
    }

    sealed interface Event {
        data class OpenEmail(val intent: Intent) : Event
        data class ShowSnackbar(val message: String) : Event
        data object ShowConsentDialog : Event
        data object ShowShortRecordingWarning : Event
    }

    val events = SingleEventFlow<Event>()

    private val stater = DynamicStateFlow(TAG, vmScope) {
        State(sessions = loadLogSessions())
    }
    val state = stater.flow

    init {
        recorderModule.state
            .onEach { recorderState ->
                stater.updateBlocking {
                    copy(
                        isRecording = recorderState.isRecording,
                        recordingStartedAt = recorderState.recordingStartedAt,
                        sessions = loadLogSessions(),
                    )
                }
            }
            .launchIn(vmScope)
    }

    private fun loadLogSessions(): List<LogSessionItem> {
        return recorderModule.getLogDirectories()
            .flatMap { dir ->
                if (!dir.exists()) return@flatMap emptyList()
                val entries = dir.listFiles() ?: return@flatMap emptyList()
                entries.filter { it.isDirectory || (it.isFile && it.extension == "zip") }
                    .map { entry ->
                        val size = if (entry.isDirectory) {
                            entry.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                        } else {
                            entry.length()
                        }
                        LogSessionItem(
                            path = entry,
                            size = size,
                            lastModified = entry.lastModified(),
                        )
                    }
            }
            .sortedByDescending { it.lastModified }
    }

    fun updateCategory(category: Category) = launch {
        stater.updateBlocking { copy(category = category) }
    }

    fun updateDescription(text: String) = launch {
        if (text.length <= 5000) {
            stater.updateBlocking { copy(description = text) }
        }
    }

    fun updateExpectedBehavior(text: String) = launch {
        if (text.length <= 5000) {
            stater.updateBlocking { copy(expectedBehavior = text) }
        }
    }

    fun selectLogSession(path: File) = launch {
        stater.updateBlocking { copy(selectedSessionPath = path) }
    }

    fun deleteLogSession(path: File) = launch {
        log(TAG) { "deleteLogSession($path)" }
        if (path.isDirectory) {
            path.deleteRecursively()
            val zip = File(path.parentFile, "${path.name}.zip")
            if (zip.exists()) zip.delete()
        } else {
            path.delete()
        }
        stater.updateBlocking {
            copy(
                sessions = loadLogSessions(),
                selectedSessionPath = if (selectedSessionPath == path) null else selectedSessionPath,
            )
        }
    }

    fun refreshLogSessions() = launch {
        stater.updateBlocking { copy(sessions = loadLogSessions()) }
    }

    fun startRecording() {
        events.tryEmit(Event.ShowConsentDialog)
    }

    fun doStartRecording() = launch {
        log(TAG) { "doStartRecording()" }
        recorderModule.startRecorder()
    }

    fun stopRecording() = launch {
        val currentState = stater.value()
        val duration = System.currentTimeMillis() - currentState.recordingStartedAt
        if (duration < 5_000) {
            events.tryEmit(Event.ShowShortRecordingWarning)
            return@launch
        }
        log(TAG) { "stopRecording()" }
        recorderModule.stopRecorder(showResultUi = false)
        stater.updateBlocking { copy(sessions = loadLogSessions()) }
    }

    fun forceStopRecording() = launch {
        log(TAG) { "forceStopRecording()" }
        recorderModule.stopRecorder(showResultUi = false)
        stater.updateBlocking { copy(sessions = loadLogSessions()) }
    }

    fun send() = launch {
        val currentState = stater.value()
        if (!currentState.canSend) return@launch

        stater.updateBlocking { copy(isSending = true) }

        try {
            val attachmentUri = currentState.selectedSessionPath?.let { sessionPath ->
                try {
                    if (sessionPath.isDirectory) {
                        debugLogZipper.zipAndGetUri(sessionPath)
                    } else if (sessionPath.extension == "zip" && sessionPath.exists()) {
                        debugLogZipper.getUriForZip(sessionPath)
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    log(TAG) { "Failed to prepare attachment: $e" }
                    events.tryEmit(
                        Event.ShowSnackbar(context.getString(eu.darken.capod.R.string.support_contact_debuglog_zip_error))
                    )
                    null
                }
            }

            val categoryTag = when (currentState.category) {
                Category.QUESTION -> "Question"
                Category.FEATURE -> "Feature"
                Category.BUG -> "Bug"
            }

            val firstWords = currentState.description.trim()
                .split("\\s+".toRegex())
                .take(8)
                .joinToString(" ")

            val subject = "[CAPod][$categoryTag] $firstWords"

            val body = buildString {
                appendLine(currentState.description.trim())
                if (currentState.isBug && currentState.expectedBehavior.isNotBlank()) {
                    appendLine()
                    appendLine("--- Expected behavior ---")
                    appendLine(currentState.expectedBehavior.trim())
                }
                appendLine()
                appendLine("--- Device info ---")
                appendLine("App: ${BuildConfigWrap.VERSION_DESCRIPTION}")
                appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            }

            val email = EmailTool.Email(
                receipients = listOf(SupportLinks.SUPPORT_EMAIL),
                subject = subject,
                body = body,
                attachment = attachmentUri,
            )

            val intent = emailTool.build(email, offerChooser = true)
            events.tryEmit(Event.OpenEmail(intent))
        } finally {
            stater.updateBlocking { copy(isSending = false) }
        }
    }

    companion object {
        private val TAG = logTag("Settings", "Support", "ContactForm", "VM")
    }
}
