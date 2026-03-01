package eu.darken.capod.main.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.glance.GlanceId
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import eu.darken.capod.common.debug.logging.Logging.Priority.ERROR
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.upgrade.UpgradeRepo
import eu.darken.capod.common.upgrade.isPro
import eu.darken.capod.monitor.core.PodMonitor
import eu.darken.capod.profiles.core.DeviceProfilesRepo

class BatteryGlanceWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun podMonitor(): PodMonitor
        fun upgradeRepo(): UpgradeRepo
        fun widgetSettings(): WidgetSettings
        fun deviceProfilesRepo(): DeviceProfilesRepo
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val ep = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)

        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        log(TAG, VERBOSE) { "provideGlance(appWidgetId=$appWidgetId)" }

        // Pre-load initial values (runs once per session, suspend OK)
        val initialIsPro = ep.upgradeRepo().isPro()
        val initialProfileId = ep.widgetSettings().getWidgetProfile(appWidgetId)
        val cachedDevice = initialProfileId?.let { ep.podMonitor().getDeviceForProfile(it) }

        provideContent {
            // Composable reads — must be outside try-catch
            val devices by ep.podMonitor().devices.collectAsState(initial = emptyList())
            val profiles by ep.deviceProfilesRepo().profiles.collectAsState(initial = emptyList())
            val upgradeInfo by ep.upgradeRepo().upgradeInfo.collectAsState(initial = null)
            val widthDp = LocalSize.current.width

            val state = try {
                val profileId = ep.widgetSettings().getWidgetProfile(appWidgetId)
                val theme = WidgetTheme.fromBundle(
                    AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
                )

                val isPro = upgradeInfo?.isPro ?: initialIsPro

                val liveDevice = devices.firstOrNull { it.meta.profile?.id == profileId }
                val device = liveDevice ?: cachedDevice?.takeIf { it.meta.profile?.id == profileId }

                val profileLabel = profileId?.let { pid ->
                    profiles.firstOrNull { it.id == pid }?.label
                }

                val isWide = getCellsForSize(widthDp.value.toInt()) >= 5

                WidgetRenderStateMapper.map(
                    context = context,
                    device = device,
                    theme = theme,
                    isPro = isPro,
                    hasConfiguredProfile = profileId != null,
                    profileLabel = profileLabel,
                    isWide = isWide,
                )
            } catch (e: Exception) {
                log(TAG, ERROR) { "provideGlance failed: ${e.asLog()}" }
                WidgetRenderState.Message(
                    theme = WidgetTheme.DEFAULT,
                    resolvedBgColor = WidgetRenderStateMapper.resolvedBgColor(context, WidgetTheme.DEFAULT),
                    resolvedTextColor = WidgetRenderStateMapper.resolvedTextColor(context, WidgetTheme.DEFAULT),
                    resolvedIconColor = WidgetRenderStateMapper.resolvedIconColor(context, WidgetTheme.DEFAULT),
                    primaryText = context.getString(eu.darken.capod.R.string.widget_error_loading_label),
                )
            }

            GlanceWidgetContent(state = state, context = context)
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val previewState = WidgetRenderState.previewDualPod(
            bgColor = WidgetRenderStateMapper.resolvedBgColor(context, WidgetTheme.DEFAULT),
            textColor = WidgetRenderStateMapper.resolvedTextColor(context, WidgetTheme.DEFAULT),
            iconColor = WidgetRenderStateMapper.resolvedIconColor(context, WidgetTheme.DEFAULT),
        )

        provideContent {
            GlanceWidgetContent(state = previewState, context = context)
        }
    }

    /**
     * Returns number of cells needed for given size of the widget.
     * https://developer.android.com/guide/practices/ui_guidelines/widget_design
     */
    private fun getCellsForSize(size: Int): Int {
        var n = 2
        while (70 * n - 30 < size) {
            ++n
}
        return n - 1
    }

    companion object {
        val TAG = logTag("Widget", "Glance")
    }
}
