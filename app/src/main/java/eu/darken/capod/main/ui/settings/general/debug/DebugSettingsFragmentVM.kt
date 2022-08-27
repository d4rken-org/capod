package eu.darken.capod.main.ui.settings.general.debug

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.autoreport.DebugSettings
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.debug.recording.core.RecorderModule
import eu.darken.capod.common.uix.ViewModel3
import eu.darken.capod.main.core.GeneralSettings
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class DebugSettingsFragmentVM @Inject constructor(
    private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val recorderModule: RecorderModule,
    private val generalSettings: GeneralSettings,
    private val debugSettings: DebugSettings,
) : ViewModel3(dispatcherProvider) {

    val state = recorderModule.state.asLiveData2()

    init {
        debugSettings.showUnfiltered.flow
            .distinctUntilChanged()
            .onEach { showUnfiltered ->
                if (showUnfiltered) {
                    log(TAG) { "Enabling 'show all' due to debug setting 'show unfiltered' enabled" }
                    generalSettings.showAll.value = true
                }
            }
            .launchInViewModel()
    }

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