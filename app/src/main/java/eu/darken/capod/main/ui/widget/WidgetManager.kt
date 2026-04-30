package eu.darken.capod.main.ui.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.debug.logging.Logging.Priority.ERROR
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class WidgetManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun refreshWidgets() {
        val manager = GlanceAppWidgetManager(context)
        refreshWidget(
            name = "battery",
            widget = BatteryGlanceWidget(),
            providerClass = BatteryGlanceWidget::class.java,
            receiverClass = WidgetProvider::class.java,
            manager = manager,
        )
        refreshWidget(
            name = "anc",
            widget = AncGlanceWidget(),
            providerClass = AncGlanceWidget::class.java,
            receiverClass = AncWidgetProvider::class.java,
            manager = manager,
        )
    }

    private suspend fun <T : GlanceAppWidget> refreshWidget(
        name: String,
        widget: T,
        providerClass: Class<T>,
        receiverClass: Class<*>,
        manager: GlanceAppWidgetManager,
    ) {
        val ids = manager.getGlanceIds(providerClass).ifEmpty {
            val appWidgetIds = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, receiverClass))
                .toList()
            if (appWidgetIds.isNotEmpty()) {
                log(TAG, WARN) { "refresh($name): Glance IDs empty, falling back to platform ids=$appWidgetIds" }
            }
            appWidgetIds.mapNotNull { appWidgetId ->
                runCatching { manager.getGlanceIdBy(appWidgetId) }
                    .onFailure { error ->
                        log(TAG, ERROR) { "refresh($name): failed to resolve widgetId=$appWidgetId: ${error.asLog()}" }
                    }
                    .getOrNull()
            }
        }

        log(TAG, VERBOSE) { "refresh($name): ids=${ids.toAppWidgetIds(manager)}" }

        ids.forEach { id ->
            runCatching { widget.update(context, id) }
                .onFailure { error ->
                    log(TAG, ERROR) { "refresh($name): update failed for id=${id.toAppWidgetId(manager)}: ${error.asLog()}" }
                }
        }
    }

    private fun List<GlanceId>.toAppWidgetIds(manager: GlanceAppWidgetManager): List<Int> =
        map { it.toAppWidgetId(manager) }

    private fun GlanceId.toAppWidgetId(manager: GlanceAppWidgetManager): Int = runCatching {
        manager.getAppWidgetId(this)
    }.getOrElse {
        AppWidgetManager.INVALID_APPWIDGET_ID
    }

    companion object {
        val TAG = logTag("Widget", "Manager")
    }
}
