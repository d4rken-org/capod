package eu.darken.capod.reaction.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.datastore.createValue
import eu.darken.capod.common.serialization.SerializationCapod
import eu.darken.capod.reaction.core.autoconnect.AutoConnectCondition
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReactionSettings @Inject constructor(
    @ApplicationContext private val context: Context,
    @SerializationCapod json: Json,
) {

    private val Context.dataStore by preferencesDataStore(
        name = "settings_reaction",
        produceMigrations = { ctx -> listOf(SharedPreferencesMigration(ctx, "settings_reaction")) }
    )

    private val dataStore: DataStore<Preferences> get() = context.dataStore

    val autoPause = dataStore.createValue("reaction.autopause.enabled", false)

    val autoPlay = dataStore.createValue("reaction.autoplay.enabled", false)

    val autoConnect = dataStore.createValue("reaction.autoconnect.enabled", false)

    val autoConnectCondition = dataStore.createValue(
        "reaction.autoconnect.condition",
        AutoConnectCondition.WHEN_SEEN,
        json,
        onErrorFallbackToDefault = true,
    )

    val showPopUpOnCaseOpen = dataStore.createValue("reaction.popup.caseopen", false)

    val showPopUpOnConnection = dataStore.createValue("reaction.popup.connected", false)

    val onePodMode = dataStore.createValue("reaction.onepod.enabled", false)

}
