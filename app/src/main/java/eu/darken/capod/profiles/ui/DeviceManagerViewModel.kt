package eu.darken.capod.profiles.ui

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.shareLatest
import eu.darken.capod.common.navigation.Nav
import eu.darken.capod.common.uix.ViewModel4
import eu.darken.capod.profiles.core.DeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class DeviceManagerViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val deviceProfilesRepo: DeviceProfilesRepo,
) : ViewModel4(dispatcherProvider) {

    val state = deviceProfilesRepo.profiles.map { profiles ->
        State(profiles = profiles)
    }.shareLatest(scope = vmScope)

    data class State(
        val profiles: List<DeviceProfile>,
    )

    fun onAddDevice() {
        log(TAG) { "onAddDevice()" }
        navTo(Nav.Main.DeviceProfileCreation())
    }

    fun onEditProfile(profile: DeviceProfile) {
        log(TAG) { "onEditProfile(): $profile" }
        navTo(Nav.Main.DeviceProfileCreation(profileId = profile.id))
    }

    fun onReorder(profiles: List<DeviceProfile>) = launch {
        log(TAG) { "onReorder(): ${profiles.size} profiles" }
        deviceProfilesRepo.reorderProfiles(profiles)
    }

    companion object {
        private val TAG = logTag("DeviceManager", "ViewModel")
    }
}
