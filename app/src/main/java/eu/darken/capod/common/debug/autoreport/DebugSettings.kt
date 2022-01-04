package eu.darken.capod.common.debug.autoreport

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.androidstarter.common.preferences.Settings
import eu.darken.capod.common.preferences.PreferenceStoreMapper
import eu.darken.capod.common.preferences.createFlowPreference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DebugSettings @Inject constructor(
    @ApplicationContext private val context: Context,
) : Settings() {

    override val preferences: SharedPreferences = context.getSharedPreferences("settings_debug", Context.MODE_PRIVATE)

    val isAutoReportEnabled = preferences.createFlowPreference("debug.bugreport.automatic.enabled", true)

    val isDebugModeEnabled = preferences.createFlowPreference("debug.mode.enabled", false)

    override val preferenceDataStore: PreferenceDataStore = object : PreferenceStoreMapper() {
        override fun getBoolean(key: String, defValue: Boolean): Boolean = when (key) {
            isAutoReportEnabled.key -> isAutoReportEnabled.value
            isDebugModeEnabled.key -> isDebugModeEnabled.value
            else -> super.getBoolean(key, defValue)
        }

        override fun putBoolean(key: String, value: Boolean) = when (key) {
            isAutoReportEnabled.key -> isAutoReportEnabled.update { value }
            isDebugModeEnabled.key -> isDebugModeEnabled.update { value }
            else -> super.putBoolean(key, value)
        }
    }

}