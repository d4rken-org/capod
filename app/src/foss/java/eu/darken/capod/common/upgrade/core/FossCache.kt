package eu.darken.capod.common.upgrade.core

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
class FossCache @Inject constructor(
    @ApplicationContext context: Context,
    @SerializationCapod json: Json,
) {

    private val Context.dataStore by preferencesDataStore(
        name = "settings_foss",
        produceMigrations = { ctx -> listOf(SharedPreferencesMigration(ctx, "settings_foss")) }
    )

    private val dataStore: DataStore<Preferences> = context.dataStore

    val upgrade = dataStore.createValue<FossUpgrade?>(
        key = "foss.upgrade",
        defaultValue = null,
        json = json,
        onErrorFallbackToDefault = true,
    )

}
