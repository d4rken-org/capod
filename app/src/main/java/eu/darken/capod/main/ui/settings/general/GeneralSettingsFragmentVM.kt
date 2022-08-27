package eu.darken.capod.main.ui.settings.general

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.uix.ViewModel3
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

@HiltViewModel
class GeneralSettingsFragmentVM @Inject constructor(
    private val handle: SavedStateHandle,
    private val dispatcherProvider: DispatcherProvider,
    private val bluetoothManager: BluetoothManager2,
) : ViewModel3(dispatcherProvider) {

    val bondedDevices = flow {
        emit(bluetoothManager.bondedDevices().toList())
    }.asLiveData2()

    companion object {
        private val TAG = logTag("Settings", "General", "VM")
    }
}