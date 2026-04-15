package eu.darken.capod.monitor.core.aap

import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.setupCommonEventHandlers
import eu.darken.capod.pods.core.apple.aap.AapConnectionManager
import eu.darken.capod.pods.core.apple.aap.AapPodState
import eu.darken.capod.pods.core.apple.aap.protocol.AapCommand
import eu.darken.capod.profiles.core.AppleDeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NcOnePodSender @Inject constructor(
    private val aapManager: AapConnectionManager,
    private val profilesRepo: DeviceProfilesRepo,
) {
    fun monitor(): Flow<Unit> = combine(
        aapManager.allStates,
        profilesRepo.profiles,
    ) { states, profiles ->
        val appleProfiles = profiles.filterIsInstance<AppleDeviceProfile>()
        states.entries
            .filter { (_, s) -> s.connectionState == AapPodState.ConnectionState.READY }
            .mapNotNull { (address, _) ->
                val profile = appleProfiles.firstOrNull { it.address == address }
                if (profile != null && profile.model.features.hasNcOneAirpod) {
                    address to profile.onePodMode
                } else {
                    null
                }
            }
    }
        .distinctUntilChanged()
        .onEach { commands ->
            for ((address, enabled) in commands) {
                try {
                    aapManager.sendCommand(address, AapCommand.SetNcWithOneAirPod(enabled))
                    log(TAG) { "Sent SetNcWithOneAirPod($enabled) to $address" }
                } catch (e: Exception) {
                    log(TAG, WARN) { "SetNcWithOneAirPod send failed for $address: $e" }
                }
            }
        }
        .map { }
        .setupCommonEventHandlers(TAG) { "ncOnePod" }

    companion object {
        private val TAG = logTag("Monitor", "NcOnePodSender")
    }
}
