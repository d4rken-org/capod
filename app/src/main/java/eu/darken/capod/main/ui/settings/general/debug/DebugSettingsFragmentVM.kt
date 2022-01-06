package eu.darken.capod.main.ui.settings.general.debug

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.debug.recording.core.RecorderModule
import eu.darken.capod.common.uix.ViewModel3
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@HiltViewModel
class DebugSettingsFragmentVM @Inject constructor(
    private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val recorderModule: RecorderModule,
) : ViewModel3(dispatcherProvider) {

    val state = recorderModule.state.asLiveData2()

    fun toggleRecorder() = launch {
        if (recorderModule.state.first().isRecording) {
            recorderModule.stopRecorder()
        } else {
            recorderModule.startRecorder()
        }
    }

    companion object {
        private val TAG = logTag("Settings", "Debug", "VM")
    }
}