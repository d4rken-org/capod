package eu.darken.capod.monitor.core.aap

import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.setupCommonEventHandlers
import eu.darken.capod.pods.core.apple.aap.AapConnectionManager
import eu.darken.capod.profiles.core.AppleDeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists IRK/ENC keys from AAP key exchange to [AppleDeviceProfile],
 * enabling BLE encrypted battery decryption with 1% granularity.
 */
@Singleton
class AapKeyPersister @Inject constructor(
    private val aapManager: AapConnectionManager,
    private val profilesRepo: DeviceProfilesRepo,
) {
    fun monitor(): Flow<Unit> = aapManager.keysReceived
        .onEach { (address, keys) ->
            val profile = profilesRepo.profiles.first()
                .filterIsInstance<AppleDeviceProfile>()
                .firstOrNull { it.address == address }

            if (profile == null) {
                log(TAG) { "No profile found for $address, skipping key persistence" }
                return@onEach
            }

            val updated = profile.copy(
                identityKey = keys.irk ?: profile.identityKey,
                encryptionKey = keys.encKey ?: profile.encryptionKey,
            )

            if (updated != profile) {
                profilesRepo.updateProfile(updated)
                log(TAG) { "Persisted keys for $address (IRK=${keys.irk != null}, ENC=${keys.encKey != null})" }
            }
        }
        .map { }
        .setupCommonEventHandlers(TAG) { "keyPersister" }

    companion object {
        private val TAG = logTag("Monitor", "AapKeyPersister")
    }
}
