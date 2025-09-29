package eu.darken.capod.main.ui.settings.general.debug

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.DebugSettings
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.uix.ViewModel3
import eu.darken.capod.main.core.GeneralSettings
import javax.inject.Inject

@HiltViewModel
class DebugSettingsFragmentVM @Inject constructor(
    private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val generalSettings: GeneralSettings,
    private val debugSettings: DebugSettings,
) : ViewModel3(dispatcherProvider) {

    companion object {
        private val TAG = logTag("Settings", "Debug", "VM")
    }
}