package eu.darken.capod.main.ui.settings.support

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.recording.core.RecorderModule
import eu.darken.capod.common.uix.ViewModel3
import javax.inject.Inject

@HiltViewModel
class SupportFragmentVM @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val recorderModule: RecorderModule,
) : ViewModel3(dispatcherProvider) {

    val recorderState = recorderModule.state.asLiveData2()

    fun startDebugLog() = launch {
        log(TAG) { "startDebugLog()" }
        recorderModule.startRecorder()
    }

    fun stopDebugLog() = launch {
        log(TAG) { "stopDebugLog()" }
        recorderModule.stopRecorder()
    }
}