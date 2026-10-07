package eu.darken.capod.reaction.core.autoconnect

import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.bluetooth.NudgeAvailability
import eu.darken.capod.common.bluetooth.NudgeCapabilityStore
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.hasApiLevel
import eu.darken.capod.pods.core.apple.aap.AapConnectionManager
import eu.darken.capod.pods.core.apple.aap.AapPodState
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import eu.darken.capod.profiles.core.AppleDeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoConnect @Inject constructor(
    private val bluetoothManager: BluetoothManager2,
    private val aapManager: AapConnectionManager,
    private val profilesRepo: DeviceProfilesRepo,
    private val nudgeCapabilityStore: NudgeCapabilityStore,
) {
    fun monitor() = combine(aapManager.allStates, profilesRepo.profiles, bluetoothManager.connectedDevices) { states, profiles, connected ->
        profiles.filterIsInstance<AppleDeviceProfile>().filter { profile ->
            val reactions = profile.reactionConfig
            val state = states[profile.address]
            val ear = state?.aapEarDetection
            reactions.autoConnect && reactions.autoConnectCondition == AutoConnectCondition.IN_EAR &&
                state?.connectionState == AapPodState.ConnectionState.READY &&
                (if (reactions.onePodMode) ear?.isEitherPodInEar == true else
                    ear?.primaryPod == AapSetting.EarDetection.PodPlacement.IN_EAR &&
                        ear.secondaryPod == AapSetting.EarDetection.PodPlacement.IN_EAR) &&
                connected.none { it.address.equals(profile.address, ignoreCase = true) }
        }.mapNotNull { it.address }
    }.distinctUntilChanged().map { addresses ->
        for (address in addresses) {
            if (hasApiLevel(37)) {
                if (!bluetoothManager.isCompanionAssociated(address)) continue
            } else if (nudgeCapabilityStore.availability.value == NudgeAvailability.BROKEN) continue
            val device = bluetoothManager.bondedDevices().first().firstOrNull { it.address == address }?.internal ?: continue
            val result = bluetoothManager.connectAudio(device)
            if (!hasApiLevel(37)) nudgeCapabilityStore.record(result)
            log(TAG) { "In-ear audio connection result=$result" }
        }
    }

    companion object {
        private val TAG = logTag("Reaction", "AutoConnect")
    }
}
