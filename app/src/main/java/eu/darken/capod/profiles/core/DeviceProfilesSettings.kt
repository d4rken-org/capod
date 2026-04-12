package eu.darken.capod.profiles.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.datastore.createValue
import eu.darken.capod.common.serialization.SerializationCapod
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceProfilesSettings @Inject constructor(
    @ApplicationContext private val context: Context,
    @SerializationCapod json: Json,
) {

    private val Context.dataStore by preferencesDataStore(
        name = "device_profiles",
        produceMigrations = { ctx -> listOf(SharedPreferencesMigration(ctx, "device_profiles")) }
    )

    private val dataStore: DataStore<Preferences> get() = context.dataStore

    val profiles = dataStore.createValue(
        "profiles.data",
        DeviceProfilesContainer(),
        json,
        onErrorFallbackToDefault = true,
    )
    val singleToMultiMigrationDone = dataStore.createValue("profiles.migration.v2.done", false)
    val defaultProfileCreated = dataStore.createValue("profiles.default.v2.created", false)
    val reactionMigrationDone = dataStore.createValue("profiles.reactions.migration.done", false)

}
