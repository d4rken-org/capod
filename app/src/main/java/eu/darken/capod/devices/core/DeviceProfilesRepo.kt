package eu.darken.capod.devices.core

import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.log
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceProfilesRepo @Inject constructor(
    private val settings: DeviceProfilesSettings,
) {

    val profiles: Flow<List<DeviceProfile>> = settings.profiles.flow

    fun addProfile(profile: DeviceProfile) {
        val currentProfiles = settings.profiles.value
        val updatedProfiles = currentProfiles + profile
        settings.profiles.value = updatedProfiles
        log(VERBOSE) { "Added device profile: ${profile.label}" }
    }

    fun updateProfile(profile: DeviceProfile) {
        val currentProfiles = settings.profiles.value
        val updatedProfiles = currentProfiles.map { 
            if (it.id == profile.id) profile else it 
        }
        settings.profiles.value = updatedProfiles
        log(VERBOSE) { "Updated device profile: ${profile.label}" }
    }

    fun removeProfile(profileId: String) {
        val currentProfiles = settings.profiles.value
        val updatedProfiles = currentProfiles.filter { it.id != profileId }
        settings.profiles.value = updatedProfiles
        log(VERBOSE) { "Removed device profile with ID: $profileId" }
    }

    fun reorderProfiles(profiles: List<DeviceProfile>) {
        settings.profiles.value = profiles.toList()
        log(VERBOSE) { "Reordered ${profiles.size} device profiles" }
    }
}