package eu.darken.capod.main.ui.widget

import android.content.Context
import android.os.Bundle
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
import eu.darken.capod.common.serialization.SerializationCapod
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WidgetSettings @Inject constructor(
    @ApplicationContext private val context: Context,
    @SerializationCapod private val json: Json,
) {

    private val Context.dataStore by preferencesDataStore(
        name = "widget_preferences",
        produceMigrations = { ctx -> listOf(SharedPreferencesMigration(ctx, "widget_preferences")) }
    )

    private val dataStore: DataStore<Preferences> get() = context.dataStore

    fun getWidgetConfig(widgetId: Int): WidgetConfig {
        val raw = runBlocking { dataStore.data.first()[configKey(widgetId)] }
        if (raw == null) {
            log(TAG, VERBOSE) { "getWidgetConfig(widgetId=$widgetId) = absent → default" }
            return WidgetConfig()
        }
        return try {
            json.decodeFromString<WidgetConfig>(raw).also {
                log(TAG, VERBOSE) { "getWidgetConfig(widgetId=$widgetId) = $it" }
            }
        } catch (e: SerializationException) {
            log(TAG) { "getWidgetConfig(widgetId=$widgetId): malformed JSON, returning default ($e)" }
            WidgetConfig()
        }
    }

    fun saveWidgetConfig(widgetId: Int, config: WidgetConfig) {
        log(TAG, VERBOSE) { "saveWidgetConfig(widgetId=$widgetId, config=$config)" }
        val encoded = json.encodeToString(config)
        runBlocking {
            dataStore.edit { prefs ->
                prefs[configKey(widgetId)] = encoded
                prefs.remove(legacyProfileKey(widgetId))
            }
        }
    }

    /**
     * One-shot migration from the legacy storage layout (separate `widget_profile_<id>` DataStore key
     * + per-widget theme in `AppWidgetManager.getAppWidgetOptions()`) to the unified
     * `widget_config_<id>` JSON. Marker-guarded inside the same `dataStore.edit {}` block so
     * concurrent saves can't be overwritten.
     *
     * Pre-reboot the legacy options Bundle still carries the user's theme; post-reboot it does not
     * (Android only persists the standard `OPTION_APPWIDGET_*` keys).
     */
    fun migrateLegacyConfigIfNeeded(widgetId: Int, legacyOptions: Bundle) {
        val legacyTheme = WidgetTheme.fromLegacyBundleOrNull(legacyOptions)
        runBlocking {
            dataStore.edit { prefs ->
                if (prefs[configKey(widgetId)] != null) return@edit
                val legacyProfileId = prefs[legacyProfileKey(widgetId)]
                if (legacyProfileId == null && legacyTheme == null) return@edit
                val migrated = WidgetConfig(
                    profileId = legacyProfileId,
                    theme = legacyTheme ?: WidgetTheme(),
                )
                log(TAG) { "migrateLegacyConfigIfNeeded(widgetId=$widgetId): writing $migrated" }
                prefs[configKey(widgetId)] = json.encodeToString(migrated)
                prefs.remove(legacyProfileKey(widgetId))
            }
        }
    }

    fun removeWidget(widgetId: Int) {
        log(TAG, VERBOSE) { "removeWidget(widgetId=$widgetId)" }
        runBlocking {
            dataStore.edit { prefs ->
                prefs.remove(configKey(widgetId))
                prefs.remove(legacyProfileKey(widgetId))
            }
        }
    }

    private fun configKey(widgetId: Int) = stringPreferencesKey("$WIDGET_CONFIG_PREFIX$widgetId")
    private fun legacyProfileKey(widgetId: Int) = stringPreferencesKey("$LEGACY_WIDGET_PROFILE_PREFIX$widgetId")

    companion object {
        private const val WIDGET_CONFIG_PREFIX = "widget_config_"
        private const val LEGACY_WIDGET_PROFILE_PREFIX = "widget_profile_"
        private val TAG = logTag("Widget", "Settings")
    }
}
