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
import eu.darken.capod.reaction.core.stem.StemActionSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StemConfigSender @Inject constructor(
    private val aapManager: AapConnectionManager,
    private val stemActionSettings: StemActionSettings,
    private val profilesRepo: DeviceProfilesRepo,
) {
    fun monitor(): Flow<Unit> = combine(
        aapManager.allStates,
        stemActionMask(),
    ) { states, mask ->
        states.entries
            .filter { (_, s) -> s.connectionState == AapPodState.ConnectionState.READY }
            .mapNotNull { (address, _) ->
                val profile = profilesRepo.profiles.first()
                    .filterIsInstance<AppleDeviceProfile>()
                    .firstOrNull { it.address == address }
                if (profile != null && profile.model.features.hasStemConfig) {
                    address to mask
                } else {
                    null
                }
            }
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

    private fun stemActionMask(): Flow<Int> = combine(
        stemActionSettings.leftSingle.flow,
        stemActionSettings.leftDouble.flow,
        stemActionSettings.leftTriple.flow,
        stemActionSettings.leftLong.flow,
        stemActionSettings.rightSingle.flow,
        stemActionSettings.rightDouble.flow,
        stemActionSettings.rightTriple.flow,
        stemActionSettings.rightLong.flow,
    ) { values ->
        var mask = 0
        if (values[0] != StemAction.NONE || values[4] != StemAction.NONE) mask = mask or 0x01 // single
        if (values[1] != StemAction.NONE || values[5] != StemAction.NONE) mask = mask or 0x02 // double
        if (values[2] != StemAction.NONE || values[6] != StemAction.NONE) mask = mask or 0x04 // triple
        if (values[3] != StemAction.NONE || values[7] != StemAction.NONE) mask = mask or 0x08 // long
        mask
    }

    companion object {
        private val TAG = logTag("Monitor", "StemConfigSender")
    }
}
