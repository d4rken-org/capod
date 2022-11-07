package eu.darken.capod.main.ui.settings.general

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.withPrevious
import eu.darken.capod.common.livedata.SingleLiveEvent
import eu.darken.capod.common.uix.ViewModel3
import eu.darken.capod.main.core.GeneralSettings
import eu.darken.capod.main.core.MonitorMode
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class GeneralSettingsFragmentVM @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    bluetoothManager: BluetoothManager2,
    private val generalSettings: GeneralSettings,
) : ViewModel3(dispatcherProvider) {

    val bondedDevices = bluetoothManager.bondedDevices()
        .map { it.toList() }
        .asLiveData2()

    val events = SingleLiveEvent<GeneralSettingsEvents>()

    init {

        generalSettings.monitorMode.flow
            .withPrevious()
            .filter { (old, new) ->
                old != new && new == MonitorMode.AUTOMATIC && generalSettings.mainDeviceAddress.value == null
            }
            .onEach { events.postValue(GeneralSettingsEvents.SelectDeviceAddressEvent) }
            .launchInViewModel()
    }

    companion object {
        private val TAG = logTag("Settings", "General", "VM")
    }
}