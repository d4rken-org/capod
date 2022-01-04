package eu.darken.capod.main.core

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.androidstarter.common.preferences.Settings
import eu.darken.capod.common.debug.autoreport.DebugSettings
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.preferences.PreferenceStoreMapper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeneralSettings @Inject constructor(
    @ApplicationContext private val context: Context,
    private val debugSettings: DebugSettings,
) : Settings() {

    override val preferences: SharedPreferences = context.getSharedPreferences("settings_general", Context.MODE_PRIVATE)

    override val preferenceDataStore: PreferenceDataStore = object : PreferenceStoreMapper() {
        override fun getBoolean(key: String, defValue: Boolean): Boolean = when (key) {
            else -> debugSettings.preferenceDataStore.getBoolean(key, defValue)
        }

        override fun putBoolean(key: String, value: Boolean) = when (key) {
            else -> debugSettings.preferenceDataStore.putBoolean(key, value)
        }
    }


    companion object {
        internal val TAG = logTag("Core", "Settings")
    }
}