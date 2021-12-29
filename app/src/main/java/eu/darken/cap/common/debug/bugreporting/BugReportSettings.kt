package eu.darken.cap.common.debug.bugreporting

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.cap.common.preferences.createFlowPreference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BugReportSettings @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val prefs by lazy {
        context.getSharedPreferences("bugreport_settings", Context.MODE_PRIVATE)
    }

    val isEnabled = prefs.createFlowPreference("bugreport.automatic.enabled", true)

}