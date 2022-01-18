package eu.darken.capod.reaction.settings.ui

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.uix.ViewModel3
import javax.inject.Inject

@HiltViewModel
class ReactionSettingsFragmentVM @Inject constructor(
    private val handle: SavedStateHandle,
    private val dispatcherProvider: DispatcherProvider,
    private val bluetoothManager: BluetoothManager2,
) : ViewModel3(dispatcherProvider) {

    val bondedDevices = bluetoothManager.bondedDevices().toList()

    companion object {
        private val TAG = logTag("Settings", "Reaction", "VM")
    }
}