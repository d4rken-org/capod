package eu.darken.capod.main.ui.settings.general.debug

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.DebugSettings
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.shareLatest
import eu.darken.capod.common.uix.ViewModel4
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

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
    }.shareLatest(scope = vmScope)

    fun setDebugModeEnabled(enabled: Boolean) {
        debugSettings.isDebugModeEnabled.value = enabled
    }

    fun setShowFakeData(enabled: Boolean) {
        debugSettings.showFakeData.value = enabled
    }

    fun setShowUnfiltered(enabled: Boolean) {
        debugSettings.showUnfiltered.value = enabled
    }

    companion object {
        private val TAG = logTag("Settings", "Debug", "VM")
    }
}
