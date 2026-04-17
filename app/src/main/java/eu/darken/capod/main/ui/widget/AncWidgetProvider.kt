package eu.darken.capod.main.ui.widget

import android.content.Context
import androidx.annotation.Keep
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import dagger.hilt.android.EntryPointAccessors
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag

@Keep
class AncWidgetProvider : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = AncGlanceWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        log(TAG) { "onDeleted(appWidgetIds=${appWidgetIds.toList()})" }
        super.onDeleted(context, appWidgetIds)
        val ep = EntryPointAccessors.fromApplication(
            context.applicationContext,
            AncGlanceWidget.AncWidgetEntryPoint::class.java,
        )
        appWidgetIds.forEach { widgetId ->
            ep.widgetSettings().removeWidget(widgetId)
        }
    }

    companion object {
        private val TAG = logTag("Widget", "ANC", "Provider")
    }
}
