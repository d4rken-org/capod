package eu.darken.capod.main.ui.widget

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.profiles.core.ProfileId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WidgetSettings @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val preferences: SharedPreferences = context.getSharedPreferences(
        "widget_preferences",
        Context.MODE_PRIVATE
    )

    fun saveWidgetProfile(widgetId: Int, profileId: ProfileId) {
        log(TAG, VERBOSE) { "saveWidgetProfile(widgetId=$widgetId, profileId=$profileId)" }
        preferences.edit {
            putString(getWidgetProfileKey(widgetId), profileId)
        }
    }

    fun getWidgetProfile(widgetId: Int): ProfileId? {
        val profileId = preferences.getString(getWidgetProfileKey(widgetId), null)
        log(TAG, VERBOSE) { "getWidgetProfile(widgetId=$widgetId) = $profileId" }
        return profileId
    }

    fun removeWidget(widgetId: Int) {
        log(TAG, VERBOSE) { "removeWidget(widgetId=$widgetId)" }
        preferences.edit {
            remove(getWidgetProfileKey(widgetId))
        }
    }

    private fun getWidgetProfileKey(widgetId: Int): String = "$WIDGET_PROFILE_PREFIX$widgetId"

    companion object {
        private const val WIDGET_PROFILE_PREFIX = "widget_profile_"
        private val TAG = logTag("Widget", "Settings")
    }
}