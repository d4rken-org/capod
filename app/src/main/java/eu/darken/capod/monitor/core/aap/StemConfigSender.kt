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
import eu.darken.capod.reaction.core.stem.StemAction
import eu.darken.capod.reaction.core.stem.StemActionsConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StemConfigSender @Inject constructor(
    private val aapManager: AapConnectionManager,
    private val profilesRepo: DeviceProfilesRepo,
) {
    fun monitor(): Flow<Unit> = combine(
        aapManager.allStates,
        profilesRepo.profiles,
    ) { states, profiles ->
        val readyAddresses = states.entries
            .filter { (_, s) -> s.connectionState == AapPodState.ConnectionState.READY }
            .map { (address, _) -> address }
            .toSet()
        profiles
            .filterIsInstance<AppleDeviceProfile>()
            .filter { it.address != null && it.address in readyAddresses && it.model.features.hasStemConfig }
            .map { profile -> profile.address!! to profile.stemActions.toMask() }
    }
        .distinctUntilChanged()
        .onEach { commands ->
            for ((address, mask) in commands) {
                try {
                    aapManager.sendCommand(address, AapCommand.SetStemConfig(mask))
                    log(TAG) { "Sent stem config 0x${"%02X".format(mask)} to $address" }
                } catch (e: Exception) {
                    log(TAG, WARN) { "StemConfig send failed for $address: $e" }
                }
            }
        }
        .map { }
        .setupCommonEventHandlers(TAG) { "stemConfig" }

    private fun StemActionsConfig.toMask(): Int {
        var mask = 0
        if (leftSingle != StemAction.NONE || rightSingle != StemAction.NONE) mask = mask or 0x01
        if (leftDouble != StemAction.NONE || rightDouble != StemAction.NONE) mask = mask or 0x02
        if (leftTriple != StemAction.NONE || rightTriple != StemAction.NONE) mask = mask or 0x04
        if (leftLong != StemAction.NONE || rightLong != StemAction.NONE) mask = mask or 0x08
        return mask
    }

    companion object {
        private val TAG = logTag("Monitor", "StemConfigSender")
    }
}
