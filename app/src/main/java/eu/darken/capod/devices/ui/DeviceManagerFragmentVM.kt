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
            if (profiles.isEmpty()) {
                listOf(
                    NoProfilesCardVH.Item(
                        onAddProfile = { onAddDevice() }
                    )
                )
            } else {
                profiles.map { profile ->
                    DeviceProfileVH.Item(
                        profile = profile,
                        onItemClick = { onEditProfile(it) }
                    )
                }
            }
        }
        .onEach { log(TAG) { "Profiles updated: ${it.size} items" } }
        .asLiveData()

    fun onAddDevice() {
        log(TAG) { "onAddDevice()" }
        DeviceManagerFragmentDirections
            .actionDeviceManagerFragmentToDeviceProfileCreationFragment()
            .navigate()
    }

    fun onBackPressed() {
        log(TAG) { "onBackPressed()" }
        navEvents.postValue(null)
    }

    private fun onEditProfile(profile: DeviceProfile) {
        log(TAG) { "onEditProfile(): $profile" }
        DeviceManagerFragmentDirections
            .actionDeviceManagerFragmentToDeviceProfileCreationFragment(profileId = profile.id)
            .navigate()
    }

    fun onProfilesReordered(items: List<DeviceManagerAdapter.Item>) {
        log(TAG) { "onProfilesReordered(): ${items.size} items" }
        val profiles = items.filterIsInstance<DeviceProfileVH.Item>().map { it.profile }
        if (profiles.isNotEmpty()) {
            launch {
                deviceProfilesRepo.reorderProfiles(profiles)
                log(TAG) { "Profiles reordered: ${profiles.map { it.label }}" }
            }
        }
    }

    companion object {
        private val TAG = logTag("DeviceManager", "ViewModel")
    }
}