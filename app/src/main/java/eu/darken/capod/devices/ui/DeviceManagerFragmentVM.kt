package eu.darken.capod.devices.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asLiveData
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.uix.ViewModel3
import eu.darken.capod.devices.core.DeviceProfile
import eu.darken.capod.devices.core.DeviceProfilesRepo
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class DeviceManagerFragmentVM @Inject constructor(
    @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val deviceProfilesRepo: DeviceProfilesRepo,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    val listItems: LiveData<List<DeviceManagerAdapter.Item>> = deviceProfilesRepo.profiles
        .map { profiles ->
            profiles.map { profile ->
                DeviceProfileVH.Item(
                    profile = profile,
                    onItemClick = { onItemClick(it) },
                    onMenuClick = { onMenuClick(it) }
                )
            }
        }
        .onEach { log(TAG) { "Profiles updated: ${it.size} items" } }
        .asLiveData()

    fun onAddDevice() {
        log(TAG) { "onAddDevice()" }
        // TODO: Navigate to device profile creation screen
    }

    fun onBackPressed() {
        log(TAG) { "onBackPressed()" }
        navEvents.postValue(null)
    }

    private fun onItemClick(profile: DeviceProfile) {
        log(TAG) { "onItemClick(): $profile" }
        // TODO: Navigate to device profile edit screen
    }

    private fun onMenuClick(profile: DeviceProfile) {
        log(TAG) { "onMenuClick(): $profile" }
        // TODO: Show menu with edit/delete options
    }

    companion object {
        private val TAG = logTag("DeviceManager", "ViewModel")
    }
}