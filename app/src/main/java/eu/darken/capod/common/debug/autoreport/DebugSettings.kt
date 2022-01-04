package eu.darken.capod.common.debug.autoreport

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.preferences.createFlowPreference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DebugSettings @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val preferences: SharedPreferences = context.getSharedPreferences("settings_debug", Context.MODE_PRIVATE)

    val isAutoReportEnabled = preferences.createFlowPreference("debug.bugreport.automatic.enabled", true)

    val isDebugModeEnabled = preferences.createFlowPreference("debug.mode.enabled", false)

}