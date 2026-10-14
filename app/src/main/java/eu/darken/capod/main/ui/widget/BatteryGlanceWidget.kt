package eu.darken.capod.main.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.annotation.Keep
import androidx.compose.runtime.remember
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
import eu.darken.capod.monitor.core.battery.BatteryEstimator
import eu.darken.capod.monitor.core.battery.takeFor
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

class BatteryGlanceWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    @Keep
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun deviceMonitor(): DeviceMonitor
        fun upgradeRepo(): UpgradeRepo
        fun widgetSettings(): WidgetSettings
        fun deviceProfilesRepo(): DeviceProfilesRepo
        fun batteryEstimator(): BatteryEstimator
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val ep: WidgetEntryPoint
        val appWidgetId: Int

        try {
            ep = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
            appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
            log(TAG, VERBOSE) { "provideGlance(appWidgetId=$appWidgetId)" }
            ep.widgetSettings().migrateLegacyConfigIfNeeded(
                appWidgetId,
                AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId),
            )
        } catch (e: Exception) {
            log(TAG, ERROR) { "provideGlance setup failed: ${e.asLog()}" }
            provideContent {
                val layout = BatteryLayout.forCells(getCellsForSize(LocalSize.current.width.value.toInt()))
                GlanceWidgetContent(
                    state = WidgetRenderState.Message(
                        theme = WidgetTheme.DEFAULT,
                        resolvedBgColor = WidgetRenderStateMapper.resolvedBgColor(context, WidgetTheme.DEFAULT),
                        resolvedTextColor = WidgetRenderStateMapper.resolvedTextColor(context, WidgetTheme.DEFAULT),
                        resolvedIconColor = WidgetRenderStateMapper.resolvedIconColor(context, WidgetTheme.DEFAULT),
                        layout = layout,
                        primaryText = context.getString(eu.darken.capod.R.string.widget_error_loading_label),
                    ),
                    context = context,
                )
            }
            return
        }

        provideContent {
            val widthDp = LocalSize.current.width
            val layout = BatteryLayout.forCells(getCellsForSize(widthDp.value.toInt()))

            val inputs = rememberWidgetInputs(
                configFlow = remember { ep.widgetSettings().widgetConfigFlow(appWidgetId) },
                initialConfig = {
                    runCatching { ep.widgetSettings().getWidgetConfig(appWidgetId) }
                        .onFailure { e -> log(TAG, ERROR) { "getWidgetConfig failed: ${e.asLog()}" } }
                        .getOrNull()
                },
                proFlow = remember { ep.upgradeRepo().upgradeInfo.map { it.isPro }.distinctUntilChanged() },
                initialPro = {
                    runCatching { runBlocking { ep.upgradeRepo().isPro() } }
                        .onFailure { e -> log(TAG, ERROR) { "isPro failed: ${e.asLog()}" } }
                        .getOrDefault(false)
                },
                labelFlow = { pid ->
                    ep.deviceProfilesRepo().profiles
                        .map { profiles -> profiles.firstOrNull { it.id == pid }?.label }
                        .distinctUntilChanged()
                },
                initialLabel = { pid ->
                    runCatching {
                        runBlocking { ep.deviceProfilesRepo().profiles.first().firstOrNull { it.id == pid }?.label }
                    }
                        .onFailure { e -> log(TAG, ERROR) { "profile label lookup failed: ${e.asLog()}" } }
                        .getOrNull()
                },
                deviceFlow = { pid -> ep.deviceMonitor().widgetDeviceFlow(pid) },
                initialDevice = { pid ->
                    runCatching { runBlocking { ep.deviceMonitor().getDeviceForProfile(pid) } }
                        .onFailure { e -> log(TAG, ERROR) { "initial device lookup failed: ${e.asLog()}" } }
                        .getOrNull()
                },
                // Empty while the monitor service isn't running — estimates simply stay hidden.
                estimateFlow = { pid -> ep.batteryEstimator().estimates.map { it[pid] }.distinctUntilChanged() },
            )
            val config = inputs.config

            val state = if (config != null) {
                val device = inputs.device

                log(TAG, VERBOSE) { "render(appWidgetId=$appWidgetId, deviceKey=${device?.toWidgetKey()})" }

                WidgetRenderStateMapper.map(
                    context = context,
                    device = device,
                    theme = config.theme,
                    isPro = inputs.isPro,
                    hasConfiguredProfile = config.profileId != null,
                    profileLabel = inputs.profileLabel,
                    layout = layout,
                    estimate = device?.let { d -> inputs.rawEstimate?.takeFor(d) },
                )
            } else {
                WidgetRenderState.Message(
                    theme = WidgetTheme.DEFAULT,
                    resolvedBgColor = WidgetRenderStateMapper.resolvedBgColor(context, WidgetTheme.DEFAULT),
                    resolvedTextColor = WidgetRenderStateMapper.resolvedTextColor(context, WidgetTheme.DEFAULT),
                    resolvedIconColor = WidgetRenderStateMapper.resolvedIconColor(context, WidgetTheme.DEFAULT),
                    layout = layout,
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
        ).withLocalizedPreviewEstimates(context)

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
        while (70 * n - 30 <= size) {
            ++n
        }
        return n - 1
    }

    companion object {
        val TAG = logTag("Widget", "Glance")
    }
}
