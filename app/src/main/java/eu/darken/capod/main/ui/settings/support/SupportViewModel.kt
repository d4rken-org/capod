package eu.darken.capod.main.ui.settings.support

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.capod.common.WebpageTool
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.debug.recording.core.RecorderModule
import eu.darken.capod.common.flow.shareLatest
import eu.darken.capod.common.navigation.Nav
import eu.darken.capod.common.uix.ViewModel4
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SupportViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val webpageTool: WebpageTool,
    private val recorderModule: RecorderModule,
) : ViewModel4(dispatcherProvider) {

    data class State(
        val isRecording: Boolean,
        val currentLogPath: File?,
    )

    val state = recorderModule.state
        .map {
            State(
                isRecording = it.isRecording,
                currentLogPath = it.currentLogPath,
            )
        }
        .shareLatest(scope = vmScope)

    fun openUrl(url: String) {
        webpageTool.open(url)
    }

    fun goToTroubleShooter() {
        navTo(Nav.Main.TroubleShooter)
    }

    fun startDebugLog() = launch {
        log(TAG) { "startDebugLog()" }
        recorderModule.startRecorder()
    }

    fun stopDebugLog() = launch {
        log(TAG) { "stopDebugLog()" }
        recorderModule.stopRecorder()
    }

    companion object {
        private val TAG = logTag("Settings", "Support", "VM")
    }
}
