package eu.darken.capod.monitor.core.aap

import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.setupCommonEventHandlers
import eu.darken.capod.pods.core.apple.aap.AapConnectionManager
import eu.darken.capod.pods.core.apple.aap.AapPodState
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import eu.darken.capod.profiles.core.AppleDeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the learned AllowOffOption and ListeningModeCycle values to [AppleDeviceProfile]
 * whenever they change in AAP state. AAP state is dropped on disconnect and neither setting
 * is proactively echoed by the device, so without persistence every reconnect would force the
 * UI back to defaults (OFF hidden, cycle mask 0x0E).
 */
@Singleton
class AapLearnedSettingsPersister @Inject constructor(
    private val aapManager: AapConnectionManager,
    private val profilesRepo: DeviceProfilesRepo,
) {
    private data class LearnedSnapshot(
        val allowOffEnabled: Boolean?,
        val cycleMask: Int?,
    )

    fun monitor(): Flow<Unit> = aapManager.allStates
        .map { states -> states.mapValues { (_, state) -> state.snapshot() } }
        .distinctUntilChanged()
        .onEach { addressToSnapshot ->
            addressToSnapshot.forEach { (address, snapshot) ->
                if (snapshot.allowOffEnabled == null && snapshot.cycleMask == null) return@forEach
                val profile = profilesRepo.profiles.first()
                    .filterIsInstance<AppleDeviceProfile>()
                    .firstOrNull { it.address == address } ?: return@forEach
                val allowOffChanged = snapshot.allowOffEnabled != null &&
                    profile.learnedAllowOffEnabled != snapshot.allowOffEnabled
                val cycleChanged = snapshot.cycleMask != null &&
                    profile.lastRequestedListeningModeCycleMask != snapshot.cycleMask
                if (!allowOffChanged && !cycleChanged) return@forEach
                profilesRepo.updateAppleProfile(profile.id) {
                    it.copy(
                        learnedAllowOffEnabled = if (allowOffChanged) snapshot.allowOffEnabled else it.learnedAllowOffEnabled,
                        lastRequestedListeningModeCycleMask = if (cycleChanged) snapshot.cycleMask else it.lastRequestedListeningModeCycleMask,
                    )
                }
                if (allowOffChanged) log(TAG) { "Persisted learnedAllowOffEnabled=${snapshot.allowOffEnabled} for $address" }
                if (cycleChanged) log(TAG) { "Persisted lastRequestedListeningModeCycleMask=0x%02X for $address".format(snapshot.cycleMask) }
            }
        }
        .map { }
        .setupCommonEventHandlers(TAG) { "learnedSettingsPersister" }

    private fun AapPodState.snapshot(): LearnedSnapshot = LearnedSnapshot(
        allowOffEnabled = setting<AapSetting.AllowOffOption>()?.enabled,
        cycleMask = setting<AapSetting.ListeningModeCycle>()?.modeMask,
    )

    companion object {
        private val TAG = logTag("Monitor", "AapLearnedSettingsPersister")
    }
}
