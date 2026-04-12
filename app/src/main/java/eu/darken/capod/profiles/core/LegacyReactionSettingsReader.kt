package eu.darken.capod.profiles.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.reaction.core.autoconnect.AutoConnectCondition
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

/**
 * One-shot reader for the legacy global `settings_reaction` DataStore. Used by the reaction
 * migration in [DeviceProfilesRepo] to copy the old global values onto every profile. Does
 * not depend on the old `ReactionSettings` class (deleted in the same change), so the
 * migration can still run after that class is gone.
 */
class LegacyReactionSettingsReader(context: Context, private val json: Json) {

    private val Context.legacyStore: DataStore<Preferences> by preferencesDataStore(
        name = LEGACY_STORE_NAME,
    )

    private val dataStore: DataStore<Preferences> = context.applicationContext.legacyStore

    suspend fun read(): LegacyReactionValues {
        val prefs = dataStore.data.first()
        val conditionRaw = prefs[CONDITION_KEY]
        val condition = conditionRaw?.let {
            try {
                json.decodeFromString<AutoConnectCondition>(it)
            } catch (e: Exception) {
                log(TAG, WARN) { "Failed to decode legacy auto-connect condition: ${e.message}" }
                null
            }
        } ?: AutoConnectCondition.WHEN_SEEN

        return LegacyReactionValues(
            autoPause = prefs[AUTO_PAUSE_KEY] ?: false,
            autoPlay = prefs[AUTO_PLAY_KEY] ?: false,
            autoConnect = prefs[AUTO_CONNECT_KEY] ?: false,
            autoConnectCondition = condition,
            showPopUpOnCaseOpen = prefs[POPUP_CASE_OPEN_KEY] ?: false,
            showPopUpOnConnection = prefs[POPUP_CONNECTED_KEY] ?: false,
            onePodMode = prefs[ONE_POD_KEY] ?: false,
        )
    }

    companion object {
        private val TAG = logTag("LegacyReactionReader")
        private const val LEGACY_STORE_NAME = "settings_reaction"

        internal val AUTO_PAUSE_KEY = booleanPreferencesKey("reaction.autopause.enabled")
        internal val AUTO_PLAY_KEY = booleanPreferencesKey("reaction.autoplay.enabled")
        internal val AUTO_CONNECT_KEY = booleanPreferencesKey("reaction.autoconnect.enabled")
        internal val CONDITION_KEY = stringPreferencesKey("reaction.autoconnect.condition")
        internal val POPUP_CASE_OPEN_KEY = booleanPreferencesKey("reaction.popup.caseopen")
        internal val POPUP_CONNECTED_KEY = booleanPreferencesKey("reaction.popup.connected")
        internal val ONE_POD_KEY = booleanPreferencesKey("reaction.onepod.enabled")
    }
}

data class LegacyReactionValues(
    val autoPause: Boolean,
    val autoPlay: Boolean,
    val autoConnect: Boolean,
    val autoConnectCondition: AutoConnectCondition,
    val showPopUpOnCaseOpen: Boolean,
    val showPopUpOnConnection: Boolean,
    val onePodMode: Boolean,
)
