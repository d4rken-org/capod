package eu.darken.capod.main.ui.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class WidgetManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun refreshWidgets() {
        BatteryGlanceWidget().updateAll(context)
        AncGlanceWidget().updateAll(context)
    }
}
