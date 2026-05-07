package eu.darken.capod.common.debug

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.BuildConfigWrap
import eu.darken.capod.common.datastore.createValue
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DebugSettings @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val Context.dataStore by preferencesDataStore(
        name = "settings_debug",
        produceMigrations = { ctx -> listOf(SharedPreferencesMigration(ctx, "settings_debug")) }
    )

    private val dataStore: DataStore<Preferences> get() = context.dataStore

    val isAutoReportingEnabled = dataStore.createValue(
        key = "debug.bugreport.automatic.enabled",
        // Reporting is opt-out for gplay, and opt-in for github builds
        defaultValue = BuildConfigWrap.FLAVOR == BuildConfigWrap.Flavor.GPLAY
    )
}
