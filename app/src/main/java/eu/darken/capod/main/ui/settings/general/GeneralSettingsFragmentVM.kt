package eu.darken.capod.main.ui.settings.general

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.capod.common.bluetooth.BluetoothDevice2
import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.withPrevious
import eu.darken.capod.common.livedata.SingleLiveEvent
import eu.darken.capod.common.uix.ViewModel3
import eu.darken.capod.main.core.GeneralSettings
import eu.darken.capod.main.core.MonitorMode
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject

@HiltViewModel
class GeneralSettingsFragmentVM @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    bluetoothManager: BluetoothManager2,
    private val generalSettings: GeneralSettings,
) : ViewModel3(dispatcherProvider) {

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

    val state = combine(
        bluetoothManager.bondedDevices().onStart { emit(emptySet()) },
        generalSettings.mainDeviceIdentityKey.flow
    ) { bondedDevices, identityKey ->
        State(
            devices = bondedDevices.toList(),
            hasIdentityKey = identityKey != null
        )
    }.asLiveData2()

    data class State(
        val devices: List<BluetoothDevice2>,
        val hasIdentityKey: Boolean
    )

    companion object {
        private val TAG = logTag("Settings", "General", "VM")
    }
}