package eu.darken.capod.monitor.core

import eu.darken.capod.common.bluetooth.NudgeAvailability
import eu.darken.capod.common.bluetooth.NudgeCapabilityStore
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.main.core.MonitorMode
import eu.darken.capod.profiles.core.DeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import eu.darken.capod.profiles.core.toReactionConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MonitorModeResolver @Inject constructor(
    private val profilesRepo: DeviceProfilesRepo,
    private val nudgeCapabilityStore: NudgeCapabilityStore,
) {

    val effectiveMode: Flow<MonitorMode> = combine(
        profilesRepo.profiles,
        nudgeCapabilityStore.availability,
    ) { profiles, nudge ->
        // The mode is driven by the first *addressed* profile, not just the first profile. An
        // address-less profile can neither host an AAP session (AapAutoConnect requires an address)
        // nor auto-connect, so it can only ever resolve to MANUAL. Letting such a profile sit at the
        // top of the list and force MANUAL would silently stop the foreground service that keeps the
        // process — and thus background AAP/stem controls — alive for an addressed profile below it.
        // No addressed profile at all => genuinely nothing to monitor => MANUAL.
        profiles.firstOrNull { !it.address.isNullOrBlank() }?.requiredMode(nudge)
            ?: MonitorMode.MANUAL
    }
        .distinctUntilChanged()
        .onEach { log(TAG, VERBOSE) { "effectiveMode = $it" } }

    private fun DeviceProfile.requiredMode(nudge: NudgeAvailability): MonitorMode = when {
        // Match AutoConnect.kt's isNullOrEmpty check — a blank/legacy "" address is no address.
        address.isNullOrBlank() -> MonitorMode.MANUAL
        toReactionConfig().autoConnect && nudge != NudgeAvailability.BROKEN -> MonitorMode.ALWAYS
        else -> MonitorMode.AUTOMATIC
    }

    companion object {
        private val TAG = logTag("Monitor", "ModeResolver")
    }
}
