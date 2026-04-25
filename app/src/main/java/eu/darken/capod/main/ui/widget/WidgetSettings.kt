package eu.darken.capod.main.ui.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.profiles.core.ProfileId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WidgetSettings @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val Context.dataStore by preferencesDataStore(
        name = "widget_preferences",
        produceMigrations = { ctx -> listOf(SharedPreferencesMigration(ctx, "widget_preferences")) }
    )

    private val dataStore: DataStore<Preferences> get() = context.dataStore

    fun saveWidgetProfile(widgetId: Int, profileId: ProfileId) {
        log(TAG, VERBOSE) { "saveWidgetProfile(widgetId=$widgetId, profileId=$profileId)" }
        runBlocking {
            dataStore.edit { it[stringPreferencesKey(getWidgetProfileKey(widgetId))] = profileId }
        }
    }

    fun getWidgetProfile(widgetId: Int): ProfileId? = runBlocking {
        dataStore.data.first()[stringPreferencesKey(getWidgetProfileKey(widgetId))]
    }

    fun removeWidget(widgetId: Int) {
        log(TAG, VERBOSE) { "removeWidget(widgetId=$widgetId)" }
        runBlocking {
            dataStore.edit { it.remove(stringPreferencesKey(getWidgetProfileKey(widgetId))) }
        }
    }

    private fun getWidgetProfileKey(widgetId: Int): String = "$WIDGET_PROFILE_PREFIX$widgetId"

    companion object {
        private const val WIDGET_PROFILE_PREFIX = "widget_profile_"
        private val TAG = logTag("Widget", "Settings")
    }
}
