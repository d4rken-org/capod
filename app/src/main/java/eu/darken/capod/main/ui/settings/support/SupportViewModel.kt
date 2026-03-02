package eu.darken.capod.main.ui.settings.support

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.capod.common.WebpageTool
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.debug.recording.core.RecorderModule
import eu.darken.capod.common.flow.DynamicStateFlow
import eu.darken.capod.common.flow.SingleEventFlow
import eu.darken.capod.common.navigation.Nav
import eu.darken.capod.common.uix.ViewModel4
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
        val logFolderSize: Long = 0L,
    )

    sealed interface Event {
        data object ShowConsentDialog : Event
        data object ShowShortRecordingWarning : Event
    }

    val events = SingleEventFlow<Event>()

    private val stater = DynamicStateFlow(TAG, vmScope) {
        State(logFolderSize = recorderModule.getLogFolderSize())
    }
    val state = stater.flow

    init {
        recorderModule.state
            .onEach { recorderState ->
                stater.updateBlocking {
                    copy(
                        isRecording = recorderState.isRecording,
                        currentLogPath = recorderState.currentLogPath,
                        recordingStartedAt = recorderState.recordingStartedAt,
                        logFolderSize = recorderModule.getLogFolderSize(),
                    )
                }
            }
            .launchIn(vmScope)
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
        val currentState = stater.value()
        val duration = System.currentTimeMillis() - currentState.recordingStartedAt
        if (duration < 5_000) {
            events.tryEmit(Event.ShowShortRecordingWarning)
            return
        }
        log(TAG) { "stopDebugLog()" }
        recorderModule.stopRecorder()
        refreshLogSize()
    }

    fun forceStopDebugLog() = launch {
        log(TAG) { "forceStopDebugLog()" }
        recorderModule.stopRecorder()
        refreshLogSize()
    }

    fun clearDebugLogs() = launch {
        log(TAG) { "clearDebugLogs()" }
        recorderModule.deleteAllLogs()
        refreshLogSize()
    }

    private suspend fun refreshLogSize() {
        stater.updateBlocking {
            copy(logFolderSize = recorderModule.getLogFolderSize())
        }
    }

    companion object {
        private val TAG = logTag("Settings", "Support", "VM")
    }
}
