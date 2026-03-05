package eu.darken.capod.main.ui.settings.support

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.capod.common.WebpageTool
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.debug.recording.core.DebugSession
import eu.darken.capod.common.debug.recording.core.RecorderModule
import eu.darken.capod.common.flow.DynamicStateFlow
import eu.darken.capod.common.flow.SingleEventFlow
import eu.darken.capod.common.navigation.Nav
import eu.darken.capod.common.uix.ViewModel4
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SupportViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val webpageTool: WebpageTool,
    private val recorderModule: RecorderModule,
) : ViewModel4(dispatcherProvider) {

    data class State(
        val isRecording: Boolean = false,
        val currentLogPath: File? = null,
        val recordingStartedAt: Long = 0L,
        val sessions: List<DebugSession> = emptyList(),
    ) {
        val logSessionCount: Int get() = sessions.count { it !is DebugSession.Recording }
        val logFolderSize: Long get() = sessions.sumOf { it.diskSize }
        val failedSessions: List<DebugSession.Failed> get() = sessions.filterIsInstance<DebugSession.Failed>()
    }

    sealed interface Event {
        data object ShowConsentDialog : Event
        data object ShowShortRecordingWarning : Event
        data class OpenRecorderActivity(val sessionId: String, val legacyPath: String?) : Event
    }

    val events = SingleEventFlow<Event>()

    private val stater = DynamicStateFlow(TAG, vmScope) { State() }
    val state = stater.flow

    init {
        combine(
            recorderModule.state,
            recorderModule.sessions,
        ) { recorderState, sessions ->
            stater.updateBlocking {
                copy(
                    isRecording = recorderState.isRecording,
                    currentLogPath = recorderState.currentLogPath,
                    recordingStartedAt = recorderState.recordingStartedAt,
                    sessions = sessions,
                )
            }
        }.launchIn(vmScope)
    }

    fun openUrl(url: String) {
        webpageTool.open(url)
    }

    fun goToTroubleShooter() {
        navTo(Nav.Main.TroubleShooter)
    }

    fun goToContactSupport() {
        navTo(Nav.Settings.ContactSupport)
    }

    fun onDebugLogToggle() = launch {
        if (stater.value().isRecording) {
            doStopDebugLog()
        } else {
            events.tryEmit(Event.ShowConsentDialog)
        }
    }

    fun startDebugLog() = launch {
        log(TAG) { "startDebugLog()" }
        recorderModule.startRecorder()
    }

    fun stopDebugLog() = launch {
        doStopDebugLog()
    }

    private suspend fun doStopDebugLog() {
        val recorderState = recorderModule.state.first()
        val duration = System.currentTimeMillis() - recorderState.recordingStartedAt
        if (duration < 5_000) {
            events.tryEmit(Event.ShowShortRecordingWarning)
            return
        }
        log(TAG) { "stopDebugLog()" }
        recorderModule.stopRecorder()
    }

    fun forceStopDebugLog() = launch {
        log(TAG) { "forceStopDebugLog()" }
        recorderModule.stopRecorder()
    }

    fun clearDebugLogs() = launch {
        log(TAG) { "clearDebugLogs()" }
        recorderModule.deleteAllLogs()
    }

    fun openSession(sessionId: String) = launch {
        val session = recorderModule.sessions.first().firstOrNull { it.id == sessionId } ?: return@launch
        val legacyPath = (session as? DebugSession.Ready)?.logDir?.path
        events.tryEmit(Event.OpenRecorderActivity(sessionId, legacyPath))
    }

    fun refreshSessions() = launch {
        recorderModule.refreshSessions()
    }

    fun deleteSession(id: String) = launch {
        log(TAG) { "deleteSession($id)" }
        recorderModule.deleteSession(id)
    }

    companion object {
        private val TAG = logTag("Settings", "Support", "VM")
    }
}
