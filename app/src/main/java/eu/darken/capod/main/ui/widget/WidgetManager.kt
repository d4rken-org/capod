package eu.darken.capod.main.ui.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class WidgetManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun refreshWidgets() {
        log(TAG) { "refreshWidgets()" }
        BatteryGlanceWidget().updateAll(context)
    }

    companion object {
        val TAG = logTag("Widget", "Manager")
    }
}
