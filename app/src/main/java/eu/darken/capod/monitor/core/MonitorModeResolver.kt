package eu.darken.capod.monitor.core

import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.main.core.MonitorMode
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MonitorModeResolver @Inject constructor(
    private val profilesRepo: DeviceProfilesRepo,
) {

    val effectiveMode: Flow<MonitorMode> = profilesRepo.profiles
        .map { profiles ->
            if (profiles.any { !it.address.isNullOrBlank() }) MonitorMode.AUTOMATIC else MonitorMode.MANUAL
        }
        .distinctUntilChanged()
        .onEach { log(TAG, VERBOSE) { "effectiveMode = $it" } }

    companion object {
        private val TAG = logTag("Monitor", "ModeResolver")
    }
}
