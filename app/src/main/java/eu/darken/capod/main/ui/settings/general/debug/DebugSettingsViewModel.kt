package eu.darken.capod.main.ui.settings.general.debug

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.DebugSettings
import eu.darken.capod.common.debug.logging.Logging.Priority.INFO
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag

import eu.darken.capod.common.uix.ViewModel4
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import eu.darken.capod.common.datastore.valueBlocking

@HiltViewModel
class DebugSettingsViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val debugSettings: DebugSettings,
) : ViewModel4(dispatcherProvider) {

    data class State(
        val isDebugModeEnabled: Boolean,
        val showFakeData: Boolean,
        val showUnfiltered: Boolean,
    )

    val state = combine(
        debugSettings.isDebugModeEnabled.flow,
        debugSettings.showFakeData.flow,
        debugSettings.showUnfiltered.flow,
    ) { debugMode, fakeData, unfiltered ->
        State(
            isDebugModeEnabled = debugMode,
            showFakeData = fakeData,
            showUnfiltered = unfiltered,
        )
    }.asLiveState()

    fun setDebugModeEnabled(enabled: Boolean) {
        log(TAG, INFO) { "setDebugModeEnabled($enabled)" }
        debugSettings.isDebugModeEnabled.valueBlocking = enabled
    }

    fun setShowFakeData(enabled: Boolean) {
        log(TAG, INFO) { "setShowFakeData($enabled)" }
        debugSettings.showFakeData.valueBlocking = enabled
    }

    fun setShowUnfiltered(enabled: Boolean) {
        log(TAG, INFO) { "setShowUnfiltered($enabled)" }
        debugSettings.showUnfiltered.valueBlocking = enabled
    }

    companion object {
        private val TAG = logTag("Settings", "Debug", "VM")
    }
}
