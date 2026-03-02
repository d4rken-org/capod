package eu.darken.capod.profiles.core

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.R
import eu.darken.capod.common.coroutine.AppScope
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.main.core.GeneralSettings
import eu.darken.capod.monitor.core.PodDeviceCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import eu.darken.capod.common.datastore.valueBlocking

@Singleton
class DeviceProfilesRepo @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    @ApplicationContext private val context: Context,
    private val generalSettings: GeneralSettings,
    private val settings: DeviceProfilesSettings,
    private val podDeviceCache: PodDeviceCache,
) {

    private val mutex = Mutex()

    init {
        scope.launch {
            if (settings.defaultProfileCreated.valueBlocking) return@launch


            log(TAG) { "Creating default profile" }
            var defaultProfile = AppleDeviceProfile(
                label = context.getString(R.string.profiles_name_default),
            )
            if (!settings.singleToMultiMigrationDone.valueBlocking) {
                log(TAG) { "Migrating settings default profile" }
                defaultProfile = defaultProfile.copy(
                    minimumSignalQuality = generalSettings.oldMinimumSignalQuality.valueBlocking,
                    model = generalSettings.oldMainDeviceModel.valueBlocking,
                    address = generalSettings.oldMainDeviceAddress.valueBlocking,
                    identityKey = generalSettings.oldMainDeviceIdentityKey.valueBlocking,
                    encryptionKey = generalSettings.oldMainDeviceEncryptionKey.valueBlocking,
                )
                settings.singleToMultiMigrationDone.valueBlocking = true
            }

            log(TAG) { "Default profile: $defaultProfile" }
            addProfile(defaultProfile)
            settings.defaultProfileCreated.valueBlocking = true
        }
    }

    val profiles: Flow<List<DeviceProfile>> = settings.profiles.flow.map { it.profiles }

    suspend fun addProfile(profile: DeviceProfile, addFirst: Boolean = false) = mutex.withLock {
        val currentContainer = settings.profiles.valueBlocking
        val updatedProfiles = currentContainer.profiles.toMutableList().apply {
            if (addFirst) add(0, profile) else add(profile)
        }.toList()
        settings.profiles.valueBlocking = DeviceProfilesContainer(updatedProfiles)
        log(VERBOSE) { "Added device profile: ${profile.label}" }
    }

    suspend fun updateProfile(profile: DeviceProfile) = mutex.withLock {
        val currentContainer = settings.profiles.valueBlocking
        val updatedProfiles = currentContainer.profiles.map {
            if (it.id == profile.id) profile else it
        }
        settings.profiles.valueBlocking = DeviceProfilesContainer(updatedProfiles)
        log(VERBOSE) { "Updated device profile: ${profile.label}" }
    }

    suspend fun removeProfile(profileId: ProfileId) = mutex.withLock {
        val currentContainer = settings.profiles.valueBlocking
        val updatedProfiles = currentContainer.profiles.filter { it.id != profileId }
        settings.profiles.valueBlocking = DeviceProfilesContainer(updatedProfiles)
        log(VERBOSE) { "Removed device profile with ID: $profileId" }
        podDeviceCache.delete(profileId)
    }

    suspend fun reorderProfiles(profiles: List<DeviceProfile>) = mutex.withLock {
        settings.profiles.valueBlocking = DeviceProfilesContainer(profiles.toList())
        log(VERBOSE) { "Reordered ${profiles.size} device profiles" }
    }

    suspend fun clear() {
        settings.profiles.valueBlocking = DeviceProfilesContainer(emptyList())
    }

    companion object {
        val TAG = logTag("Profiles", "Repo")
    }
}