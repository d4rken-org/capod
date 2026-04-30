package eu.darken.capod.profiles.core

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.R
import eu.darken.capod.common.coroutine.AppScope
import eu.darken.capod.common.datastore.valueBlocking
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.serialization.SerializationCapod
import eu.darken.capod.main.core.GeneralSettings
import eu.darken.capod.monitor.core.cache.DeviceStateCache
import eu.darken.capod.reaction.core.autoconnect.AutoConnectCondition
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceProfilesRepo @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    @ApplicationContext private val context: Context,
    private val generalSettings: GeneralSettings,
    private val settings: DeviceProfilesSettings,
    private val deviceStateCache: DeviceStateCache,
    @SerializationCapod private val json: Json,
) {

    private val mutex = Mutex()

    init {
        scope.launch {
            if (!settings.defaultProfileCreated.valueBlocking) {
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

            // Reaction migration runs INDEPENDENTLY of defaultProfileCreated. Existing
            // installs already have defaultProfileCreated=true, so nesting this inside the
            // block above would silently swallow the migration on upgrade.
            if (!settings.reactionMigrationDone.valueBlocking) {
                migrateLegacyReactions()
            }

            detectLegacyReactionData()
        }
    }

    /**
     * Detects whether this install ever wrote to the pre-migration global reaction DataStore.
     * The legacy reader is a read-only probe; the legacy keys persist on old installs even
     * after [migrateLegacyReactions] has run, so this check works retroactively for users who
     * already migrated. Used by the Overview hint to target only existing users who actually
     * configured reactions before the per-device move.
     */
    private suspend fun detectLegacyReactionData() {
        val hadData = try {
            val legacy = LegacyReactionSettingsReader(context, json).read()
            legacy.autoPause || legacy.autoPlay || legacy.autoConnect ||
                legacy.showPopUpOnCaseOpen || legacy.showPopUpOnConnection ||
                legacy.onePodMode
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to detect legacy reaction data: ${e.message}" }
            false
        }
        if (hadData != settings.hadLegacyReactionData.valueBlocking) {
            settings.hadLegacyReactionData.valueBlocking = hadData
        }
    }

    private suspend fun migrateLegacyReactions() {
        log(TAG) { "Migrating legacy global reaction settings onto profiles" }
        mutex.withLock {
            val legacy = try {
                LegacyReactionSettingsReader(context, json).read()
            } catch (e: Exception) {
                log(TAG, WARN) { "Failed to read legacy reactions, defaulting all profiles: ${e.message}" }
                null
            }
            val current = settings.profiles.valueBlocking.profiles
            val migrated = current.map { p ->
                if (p !is AppleDeviceProfile || legacy == null) return@map p
                val features = p.model.features
                val coercedCondition = when {
                    legacy.autoConnectCondition == AutoConnectCondition.IN_EAR && !features.hasEarDetection ->
                        AutoConnectCondition.WHEN_SEEN
                    legacy.autoConnectCondition == AutoConnectCondition.CASE_OPEN && !features.hasCase ->
                        AutoConnectCondition.WHEN_SEEN
                    else -> legacy.autoConnectCondition
                }
                p.copy(
                    autoPause = legacy.autoPause,
                    autoPlay = legacy.autoPlay,
                    onePodMode = legacy.onePodMode && features.hasDualPods && features.hasEarDetection,
                    autoConnect = legacy.autoConnect,
                    autoConnectCondition = coercedCondition,
                    showPopUpOnCaseOpen = legacy.showPopUpOnCaseOpen,
                    showPopUpOnConnection = legacy.showPopUpOnConnection,
                )
            }
            settings.profiles.valueBlocking = DeviceProfilesContainer(migrated)
            settings.reactionMigrationDone.valueBlocking = true
        }
        log(TAG) { "Legacy reaction migration complete" }
    }

    val profiles: Flow<List<DeviceProfile>> = settings.profiles.flow.map { it.profiles }

    val hadLegacyReactionData: Flow<Boolean> = settings.hadLegacyReactionData.flow

    suspend fun addProfile(profile: DeviceProfile, addFirst: Boolean = false) = mutex.withLock {
        val currentContainer = settings.profiles.valueBlocking
        checkAddressUniqueness(profile, currentContainer.profiles)
        val updatedProfiles = currentContainer.profiles.toMutableList().apply {
            if (addFirst) add(0, profile) else add(profile)
        }.toList()
        settings.profiles.valueBlocking = DeviceProfilesContainer(updatedProfiles)
        log(VERBOSE) { "Added device profile: ${profile.label}" }
    }

    suspend fun updateProfile(profile: DeviceProfile) = mutex.withLock {
        val currentContainer = settings.profiles.valueBlocking
        val otherProfiles = currentContainer.profiles.filter { it.id != profile.id }
        checkAddressUniqueness(profile, otherProfiles)
        val updatedProfiles = currentContainer.profiles.map {
            if (it.id == profile.id) profile else it
        }
        settings.profiles.valueBlocking = DeviceProfilesContainer(updatedProfiles)
        log(VERBOSE) { "Updated device profile: ${profile.label}" }
    }

    /**
     * Read-modify-write helper that preserves every field the caller doesn't touch.
     * Prevents callers that only know a subset of fields (e.g. the profile editor holding
     * name/model/keys/address/signalQuality) from silently wiping fields the user set
     * elsewhere — notably the reaction toggles edited from Device Settings.
     */
    suspend fun updateAppleProfile(
        id: ProfileId,
        transform: (AppleDeviceProfile) -> AppleDeviceProfile,
    ) = mutex.withLock {
        val currentContainer = settings.profiles.valueBlocking
        val existing = currentContainer.profiles.firstOrNull { it.id == id } as? AppleDeviceProfile
        if (existing == null) {
            log(TAG, WARN) { "updateAppleProfile($id): profile not found or not AppleDeviceProfile" }
            return@withLock
        }
        val updated = transform(existing)
        val otherProfiles = currentContainer.profiles.filter { it.id != id }
        checkAddressUniqueness(updated, otherProfiles)
        val updatedProfiles = currentContainer.profiles.map { if (it.id == id) updated else it }
        settings.profiles.valueBlocking = DeviceProfilesContainer(updatedProfiles)
        log(VERBOSE) { "Updated apple device profile: ${updated.label}" }
    }

    suspend fun removeProfile(profileId: ProfileId) = mutex.withLock {
        val currentContainer = settings.profiles.valueBlocking
        val updatedProfiles = currentContainer.profiles.filter { it.id != profileId }
        settings.profiles.valueBlocking = DeviceProfilesContainer(updatedProfiles)
        log(VERBOSE) { "Removed device profile with ID: $profileId" }
        deviceStateCache.delete(profileId)
    }

    suspend fun reorderProfilesById(orderedIds: List<ProfileId>) = mutex.withLock {
        val current = settings.profiles.valueBlocking.profiles
        require(orderedIds.size == current.size && orderedIds.toSet() == current.map { it.id }.toSet()) {
            "Reorder IDs must match current profile IDs exactly"
        }
        val byId = current.associateBy { it.id }
        val reordered = orderedIds.map { byId.getValue(it) }
        settings.profiles.valueBlocking = DeviceProfilesContainer(reordered)
        log(VERBOSE) { "Reordered ${reordered.size} device profiles by ID" }
    }

    suspend fun clear() = mutex.withLock {
        settings.profiles.valueBlocking = DeviceProfilesContainer(emptyList())
        deviceStateCache.deleteAll()
    }

    private fun checkAddressUniqueness(profile: DeviceProfile, existingProfiles: List<DeviceProfile>) {
        val address = profile.address ?: return
        val conflict = existingProfiles.find { it.address?.equals(address, ignoreCase = true) == true }
        if (conflict != null) {
            throw AddressAlreadyClaimedException(address, conflict.label)
        }
    }

    companion object {
        val TAG = logTag("Profiles", "Repo")
    }
}