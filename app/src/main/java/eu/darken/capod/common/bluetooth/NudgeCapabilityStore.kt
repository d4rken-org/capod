package eu.darken.capod.common.bluetooth

import eu.darken.capod.common.coroutine.AppScope
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.datastore.value
import eu.darken.capod.common.datastore.valueBlocking
import eu.darken.capod.common.debug.logging.Logging.Priority.INFO
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.main.core.GeneralSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NudgeCapabilityStore @Inject constructor(
    generalSettings: GeneralSettings,
    @AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) {

    private val persistedValue = generalSettings.nudgeAvailability

    // Resolve the persisted value synchronously at construction so consumers that read
    // `availability.value` (resolver, AutoConnect precondition, UI state) never see the
    // synthetic UNKNOWN seed before the first DataStore emission arrives. On a known-broken
    // device this prevents one bonus ALWAYS-mode + nudge-attempt cycle per cold start.
    val availability: StateFlow<NudgeAvailability> = persistedValue.flow
        .stateIn(
            scope = appScope + dispatcherProvider.IO,
            started = SharingStarted.Eagerly,
            initialValue = persistedValue.valueBlocking,
        )

    fun record(result: NudgeAttemptResult) {
        val verdict = verdictFor(result) ?: return
        if (availability.value == verdict) return
        log(TAG, INFO) { "Recording nudge verdict: $verdict (from $result)" }
        appScope.launch(dispatcherProvider.IO) {
            persistedValue.value(verdict)
        }
    }

    companion object {
        private val TAG = logTag("Bluetooth", "NudgeCapabilityStore")

        internal fun verdictFor(result: NudgeAttemptResult): NudgeAvailability? = when (result) {
            NudgeAttemptResult.Accepted -> NudgeAvailability.AVAILABLE
            NudgeAttemptResult.UnavailableHiddenApi -> NudgeAvailability.BROKEN
            NudgeAttemptResult.Rejected,
            NudgeAttemptResult.UnavailableMissingPermission -> null
        }
    }
}
