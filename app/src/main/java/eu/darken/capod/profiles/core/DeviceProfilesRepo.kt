package eu.darken.capod.profiles.core

import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceProfilesRepo @Inject constructor(
    private val settings: DeviceProfilesSettings,
) {

    val profiles: Flow<List<DeviceProfile>> = settings.profiles.flow.map { it.profiles }

    fun addProfile(profile: DeviceProfile) {
        val currentContainer = settings.profiles.value
        val updatedProfiles = currentContainer.profiles + profile
        settings.profiles.value = DeviceProfilesContainer(updatedProfiles)
        log(VERBOSE) { "Added device profile: ${profile.label}" }
    }

    fun updateProfile(profile: DeviceProfile) {
        val currentContainer = settings.profiles.value
        val updatedProfiles = currentContainer.profiles.map { 
            if (it.id == profile.id) profile else it 
        }
        settings.profiles.value = DeviceProfilesContainer(updatedProfiles)
        log(VERBOSE) { "Updated device profile: ${profile.label}" }
    }

    fun removeProfile(profileId: String) {
        val currentContainer = settings.profiles.value
        val updatedProfiles = currentContainer.profiles.filter { it.id != profileId }
        settings.profiles.value = DeviceProfilesContainer(updatedProfiles)
        log(VERBOSE) { "Removed device profile with ID: $profileId" }
    }

    fun reorderProfiles(profiles: List<DeviceProfile>) {
        settings.profiles.value = DeviceProfilesContainer(profiles.toList())
        log(VERBOSE) { "Reordered ${profiles.size} device profiles" }
    }
}