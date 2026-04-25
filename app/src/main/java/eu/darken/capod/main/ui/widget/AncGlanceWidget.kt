package eu.darken.capod.main.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.annotation.Keep
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
import eu.darken.capod.monitor.core.DeviceMonitor
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.profiles.core.DeviceProfilesRepo

class AncGlanceWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    @Keep
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AncWidgetEntryPoint {
        fun deviceMonitor(): DeviceMonitor
        fun upgradeRepo(): UpgradeRepo
        fun widgetSettings(): WidgetSettings
        fun deviceProfilesRepo(): DeviceProfilesRepo
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val ep: AncWidgetEntryPoint
        val appWidgetId: Int
        val initialIsPro: Boolean
        val initialProfileId: String?
        val cachedDevice: PodDevice?

        try {
            ep = EntryPointAccessors.fromApplication(context, AncWidgetEntryPoint::class.java)
            appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
            log(TAG, VERBOSE) { "provideGlance(appWidgetId=$appWidgetId)" }
            initialIsPro = ep.upgradeRepo().isPro()
            ep.widgetSettings().migrateLegacyConfigIfNeeded(
                appWidgetId,
                AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId),
            )
            initialProfileId = ep.widgetSettings().getWidgetConfig(appWidgetId).profileId
            cachedDevice = initialProfileId?.let { ep.deviceMonitor().getDeviceForProfile(it) }
        } catch (e: Exception) {
            log(TAG, ERROR) { "provideGlance setup failed: ${e.asLog()}" }
            provideContent {
                GlanceAncWidgetContent(
                    state = AncWidgetRenderState.Message(
                        theme = WidgetTheme.DEFAULT,
                        resolvedBgColor = WidgetRenderStateMapper.resolvedBgColor(context, WidgetTheme.DEFAULT),
                        resolvedTextColor = WidgetRenderStateMapper.resolvedTextColor(context, WidgetTheme.DEFAULT),
                        resolvedIconColor = WidgetRenderStateMapper.resolvedIconColor(context, WidgetTheme.DEFAULT),
                        primaryText = context.getString(eu.darken.capod.R.string.widget_error_loading_label),
                    ),
                    context = context,
                    appWidgetId = appWidgetId(context, id),
                )
            }
            return
        }

        provideContent {
            val devices by ep.deviceMonitor().devices.collectAsState(initial = emptyList())
            val profiles by ep.deviceProfilesRepo().profiles.collectAsState(initial = emptyList())
            val upgradeInfo by ep.upgradeRepo().upgradeInfo.collectAsState(initial = null)
            val widthDp = LocalSize.current.width
            val heightDp = LocalSize.current.height

            val state = try {
                val config = ep.widgetSettings().getWidgetConfig(appWidgetId)
                val profileId = config.profileId
                val theme = config.theme

                val isPro = upgradeInfo?.isPro ?: initialIsPro

                val liveDevice = devices.firstOrNull { it.profileId == profileId }
                val device = liveDevice ?: cachedDevice?.takeIf { it.profileId == profileId }

                val profileLabel = profileId?.let { pid ->
                    profiles.firstOrNull { it.id == pid }?.label
                }

                val widthCells = getCellsForSize(widthDp.value.toInt())
                val heightCells = getCellsForSize(heightDp.value.toInt())
                val layout = when {
                    widthCells <= 1 && heightCells <= 1 -> AncLayout.QUAD_CORNERS
                    heightCells <= 1 -> AncLayout.ROW_ICONS
                    widthCells <= 1 -> AncLayout.COLUMN_ICONS
                    widthCells > 2 -> AncLayout.ROW
                    heightCells > 3 -> AncLayout.COLUMN
                    else -> AncLayout.GRID_2X2
                }

                AncWidgetRenderStateMapper.map(
                    context = context,
                    device = device,
                    theme = theme,
                    isPro = isPro,
                    hasConfiguredProfile = profileId != null,
                    profileLabel = profileLabel,
                    layout = layout,
                )
            } catch (e: Exception) {
                log(TAG, ERROR) { "provideGlance failed: ${e.asLog()}" }
                AncWidgetRenderState.Message(
                    theme = WidgetTheme.DEFAULT,
                    resolvedBgColor = WidgetRenderStateMapper.resolvedBgColor(context, WidgetTheme.DEFAULT),
                    resolvedTextColor = WidgetRenderStateMapper.resolvedTextColor(context, WidgetTheme.DEFAULT),
                    resolvedIconColor = WidgetRenderStateMapper.resolvedIconColor(context, WidgetTheme.DEFAULT),
                    primaryText = context.getString(eu.darken.capod.R.string.widget_error_loading_label),
                )
            }

            GlanceAncWidgetContent(state = state, context = context, appWidgetId = appWidgetId)
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val previewState = AncWidgetRenderState.previewActive(
            context = context,
            bgColor = WidgetRenderStateMapper.resolvedBgColor(context, WidgetTheme.DEFAULT),
            textColor = WidgetRenderStateMapper.resolvedTextColor(context, WidgetTheme.DEFAULT),
            iconColor = WidgetRenderStateMapper.resolvedIconColor(context, WidgetTheme.DEFAULT),
            activeColor = WidgetRenderStateMapper.resolveThemeColor(context, com.google.android.material.R.attr.colorSecondaryContainer),
            onActiveColor = WidgetRenderStateMapper.resolveThemeColor(context, com.google.android.material.R.attr.colorOnSecondaryContainer),
        )

        provideContent {
            GlanceAncWidgetContent(
                state = previewState,
                context = context,
                appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID,
            )
        }
    }

    private fun appWidgetId(context: Context, id: GlanceId): Int {
        return try {
            GlanceAppWidgetManager(context).getAppWidgetId(id)
        } catch (_: Exception) {
            AppWidgetManager.INVALID_APPWIDGET_ID
        }
    }

    private fun getCellsForSize(size: Int): Int {
        var n = 2
        while (70 * n - 30 <= size) {
            ++n
        }
        return n - 1
    }

    companion object {
        val TAG = logTag("Widget", "ANC", "Glance")
    }
}
