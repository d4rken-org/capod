package eu.darken.capod.common.bluetooth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.coroutine.AppScope
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.datastore.createValue
import eu.darken.capod.common.datastore.value
import eu.darken.capod.common.debug.logging.Logging.Priority.INFO
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.serialization.SerializationCapod
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NudgeCapabilityStore @Inject constructor(
    @ApplicationContext private val context: Context,
    @SerializationCapod json: Json,
    @AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) {

    private val Context.bluetoothDataStore by preferencesDataStore(name = "bluetooth_state")

    private val dataStore: DataStore<Preferences> get() = context.bluetoothDataStore

    private val persistedValue = dataStore.createValue(
        "core.bluetooth.nudge.availability",
        NudgeAvailability.UNKNOWN,
        json,
        onErrorFallbackToDefault = true,
    )

    val availability: StateFlow<NudgeAvailability> = persistedValue.flow
        .stateIn(
            scope = appScope + dispatcherProvider.IO,
            started = SharingStarted.Eagerly,
            initialValue = NudgeAvailability.UNKNOWN,
        )

    fun record(result: NudgeAttemptResult) {
        val verdict = when (result) {
            NudgeAttemptResult.Accepted -> NudgeAvailability.AVAILABLE
            NudgeAttemptResult.UnavailableHiddenApi -> NudgeAvailability.BROKEN
            NudgeAttemptResult.Rejected,
            NudgeAttemptResult.UnavailableMissingPermission -> null
        }
        if (verdict == null) return
        if (availability.value == verdict) return
        log(TAG, INFO) { "Recording nudge verdict: $verdict (from $result)" }
        appScope.launch(dispatcherProvider.IO) {
            persistedValue.value(verdict)
        }
    }

    companion object {
        private val TAG = logTag("Bluetooth", "NudgeCapabilityStore")
    }
}
