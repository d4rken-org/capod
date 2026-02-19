package eu.darken.capod.main.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.LayoutRes
import androidx.appcompat.view.ContextThemeWrapper
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.capod.R
import eu.darken.capod.common.coroutine.AppScope
import eu.darken.capod.common.debug.logging.Logging.Priority.ERROR
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.upgrade.UpgradeRepo
import eu.darken.capod.common.upgrade.isPro
import eu.darken.capod.main.ui.MainActivity
import eu.darken.capod.monitor.core.PodDeviceCache
import eu.darken.capod.monitor.core.PodMonitor
import eu.darken.capod.pods.core.DualPodDevice
import eu.darken.capod.pods.core.HasCase
import eu.darken.capod.pods.core.HasChargeDetectionDual
import eu.darken.capod.pods.core.HasEarDetection
import eu.darken.capod.pods.core.HasEarDetectionDual
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.PodFactory
import eu.darken.capod.pods.core.SinglePodDevice
import eu.darken.capod.pods.core.formatBatteryPercent
import eu.darken.capod.pods.core.getBatteryDrawable
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import eu.darken.capod.profiles.core.ProfileId
import finish2
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.time.Duration
import javax.inject.Inject


@AndroidEntryPoint
class WidgetProvider : AppWidgetProvider() {

    @Inject lateinit var podMonitor: PodMonitor
    @Inject lateinit var podDeviceCache: PodDeviceCache
    @Inject lateinit var podFactory: PodFactory
    @Inject lateinit var upgradeRepo: UpgradeRepo
    @Inject lateinit var widgetSettings: WidgetSettings
    @Inject lateinit var deviceProfilesRepo: DeviceProfilesRepo
    @AppScope @Inject lateinit var appScope: CoroutineScope

    private fun executeAsync(
        tag: String,
        timeout: Duration = Duration.ofSeconds(7),
        block: suspend () -> Unit
    ) {
        val start = System.currentTimeMillis()
        val asyncBarrier = goAsync()
        log(TAG, VERBOSE) { "executeAsync($tag) starting asyncBarrier=$asyncBarrier " }

        appScope.launch {
            try {
                withTimeout(timeout.toMillis()) { block() }
            } catch (e: Exception) {
                log(TAG, ERROR) { "executeAsync($tag) failed: ${e.asLog()}" }
            } finally {
                asyncBarrier.finish2()
                val stop = System.currentTimeMillis()
                log(TAG, VERBOSE) { "executeAsync($tag) DONE (${stop - start}ms) " }
            }
        }

        log(TAG, VERBOSE) { "executeAsync($block) leaving" }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        log(TAG) { "onUpdate(appWidgetIds=${appWidgetIds.toList()})" }
        executeAsync("onUpdate") {
            appWidgetIds.forEach { appWidgetId ->
                updateWidget(context, appWidgetManager, appWidgetId, null)
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        log(TAG) { "onAppWidgetOptionsChanged(appWidgetId=$appWidgetId, newOptions=$newOptions)" }
        executeAsync("onAppWidgetOptionsChanged") {
            updateWidget(context, appWidgetManager, appWidgetId, newOptions)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        log(TAG) { "onDeleted(appWidgetIds=${appWidgetIds.toList()})" }
        executeAsync("onDeleted") {
            appWidgetIds.forEach { widgetId ->
                widgetSettings.removeWidget(widgetId)
            }
        }
    }

    /**
     * Returns number of cells needed for given size of the widget.
     *
     * The value is determined in accordance with official guidelines
     * for designing widgets, see:
     * https://developer.android.com/guide/practices/ui_guidelines/widget_design
     *
     * Thanks to Jakub S. on Stackoverflow for this solution:
     * https://stackoverflow.com/a/37522648/10866268
     *
     * @param size Widget size in dp.
     * @return Size in number of cells.
     */
    private fun getCellsForSize(size: Int): Int {
        var n = 2
        while (70 * n - 30 < size) {
            ++n
        }
        return n - 1
    }

    private suspend fun updateWidget(
        context: Context,
        widgetManager: AppWidgetManager,
        widgetId: Int,
        options: Bundle?
    ) {
        val profileId: ProfileId? = widgetSettings.getWidgetProfile(widgetId)
        log(TAG) { "updateWidget(widgetId=$widgetId, profileId=$profileId options=$options)" }

        val device: PodDevice? = profileId?.let { podMonitor.getDeviceForProfile(it) }
        val profileLabel: String? = profileId?.let { id ->
            deviceProfilesRepo.profiles.first().firstOrNull { it.id == id }?.label
        }

        val theme = WidgetTheme.fromBundle(widgetManager.getAppWidgetOptions(widgetId))
        log(TAG, VERBOSE) { "updateWidget: theme=$theme" }

        val layout = when {
            !upgradeRepo.isPro() -> createUpgradeRequiredLayout(context, widgetId, theme)
            device is DualPodDevice -> {
                val minWidth = widgetManager.getAppWidgetOptions(widgetId)
                    .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
                val columns = getCellsForSize(minWidth)

                /* Enable wide widgets only when we are provided with 5 or
                 * more columns of space.
                 * Although the minimum size is only 2x1, the safeguards are
                 * added if these restrictions will ever be loosened in the future.
                 */
                val layout = when (columns) {
                    in 1..4 -> R.layout.widget_pod_dual_compact_layout
                    else -> R.layout.widget_pod_dual_wide_layout
                }

                createDualPodLayout(context, device, layout, widgetId, theme, profileLabel)
            }

            device is SinglePodDevice -> createSinglePodLayout(context, device, widgetId, theme, profileLabel)
            device is PodDevice -> createUnknownPodLayout(context, device, widgetId, theme)
            else -> createNoDeviceLayout(context, profileId != null, widgetId, theme)
        }
        widgetManager.updateAppWidget(widgetId, layout)
    }

    private fun applyThemeColors(
        context: Context,
        views: RemoteViews,
        theme: WidgetTheme,
        textViewIds: List<Int>,
        iconViewIds: List<Int>,
        hasDeviceLabel: Boolean,
    ) {
        // Always explicitly set background color to ensure previous custom colors are overwritten
        val bgColor = theme.backgroundColor
        if (bgColor != null) {
            val colorWithAlpha = WidgetTheme.applyAlpha(bgColor, theme.backgroundAlpha)
            views.setInt(R.id.widget_root, "setBackgroundColor", colorWithAlpha)
        } else {
            // Reset to theme default — resolve ?android:attr/colorBackground
            val defaultBg = resolveThemeColor(context, android.R.attr.colorBackground)
            views.setInt(R.id.widget_root, "setBackgroundColor", defaultBg)
        }

        // Always explicitly set text/icon colors
        val fgColor = theme.foregroundColor
        if (fgColor != null) {
            for (textViewId in textViewIds) {
                views.setTextColor(textViewId, fgColor)
            }
            for (iconViewId in iconViewIds) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    views.setColorStateList(iconViewId, "setImageTintList", ColorStateList.valueOf(fgColor))
                } else {
                    views.setInt(iconViewId, "setColorFilter", fgColor)
                }
            }
        } else {
            // Reset to theme defaults
            val defaultTextColor = resolveThemeColor(context, android.R.attr.textColorPrimary)
            val defaultIconColor = resolveThemeColor(context, android.R.attr.colorAccent)
            for (textViewId in textViewIds) {
                views.setTextColor(textViewId, defaultTextColor)
            }
            for (iconViewId in iconViewIds) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    views.setColorStateList(iconViewId, "setImageTintList", ColorStateList.valueOf(defaultIconColor))
                } else {
                    views.setInt(iconViewId, "setColorFilter", defaultIconColor)
                }
            }
        }

        if (hasDeviceLabel) {
            views.setViewVisibility(
                R.id.headphones_label,
                if (theme.showDeviceLabel) View.VISIBLE else View.GONE
            )
        }
    }

    private fun resolveThemeColor(context: Context, attr: Int): Int {
        val themedContext = ContextThemeWrapper(context, com.google.android.material.R.style.Theme_Material3_DynamicColors_DayNight)
        val typedArray = themedContext.theme.obtainStyledAttributes(intArrayOf(attr))
        val color = typedArray.getColor(0, android.graphics.Color.BLACK)
        typedArray.recycle()
        return color
    }

    private suspend fun createUpgradeRequiredLayout(
        context: Context,
        widgetId: Int,
        theme: WidgetTheme,
    ) = RemoteViews(context.packageName, R.layout.widget_message_layout).apply {
        log(TAG, VERBOSE) { "createUpgradeRequiredLayout(context=$context)" }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            widgetId,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        setOnClickPendingIntent(R.id.widget_root, pendingIntent)

        setTextViewText(R.id.primary, context.getString(R.string.upgrade_capod_label))
        setTextViewText(R.id.secondary, context.getString(R.string.upgrade_capod_description))
        setViewVisibility(R.id.secondary, View.VISIBLE)

        applyThemeColors(
            context = context,
            views = this,
            theme = theme,
            textViewIds = listOf(R.id.primary, R.id.secondary),
            iconViewIds = emptyList(),
            hasDeviceLabel = false,
        )
    }

    private fun createUnknownPodLayout(
        context: Context,
        podDevice: PodDevice,
        widgetId: Int,
        theme: WidgetTheme,
    ): RemoteViews = RemoteViews(context.packageName, R.layout.widget_message_layout).apply {
        log(TAG, VERBOSE) { "createUnknownPodLayout(context=$context, podDevice=$podDevice)" }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            widgetId,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        setOnClickPendingIntent(R.id.widget_root, pendingIntent)

        setTextViewText(R.id.primary, context.getString(R.string.pods_unknown_label))

        applyThemeColors(
            context = context,
            views = this,
            theme = theme,
            textViewIds = listOf(R.id.primary),
            iconViewIds = emptyList(),
            hasDeviceLabel = false,
        )
    }

    private fun createNoDeviceLayout(
        context: Context,
        hasConfiguredProfile: Boolean = false,
        widgetId: Int,
        theme: WidgetTheme,
    ): RemoteViews = RemoteViews(context.packageName, R.layout.widget_message_layout).apply {
        log(TAG, VERBOSE) { "createNoDeviceLayout(context=$context, hasConfiguredProfile=$hasConfiguredProfile)" }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            widgetId,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        setOnClickPendingIntent(R.id.widget_root, pendingIntent)

        val messageRes = if (hasConfiguredProfile) {
            R.string.widget_no_data_label
        } else {
            R.string.overview_nomaindevice_label
        }
        setTextViewText(R.id.primary, context.getString(messageRes))

        applyThemeColors(
            context = context,
            views = this,
            theme = theme,
            textViewIds = listOf(R.id.primary),
            iconViewIds = emptyList(),
            hasDeviceLabel = false,
        )
    }

    private fun createDualPodLayout(
        context: Context,
        podDevice: DualPodDevice,
        @LayoutRes layout: Int,
        widgetId: Int,
        theme: WidgetTheme,
        profileLabel: String?,
    ): RemoteViews = RemoteViews(context.packageName, layout).apply {
        log(TAG, VERBOSE) { "createDualPodLayout(context=$context, podDevice=$podDevice), layout=${layout}" }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            widgetId,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        setOnClickPendingIntent(R.id.widget_root, pendingIntent)

        setTextViewText(R.id.headphones_label, profileLabel ?: podDevice.getLabel(context))

        // Left
        val leftPercent = podDevice.batteryLeftPodPercent
        setImageViewResource(R.id.pod_left_icon, podDevice.leftPodIcon)
        setTextViewText(R.id.pod_left_label, formatBatteryPercent(context, leftPercent))
        setViewVisibility(
            R.id.pod_left_charging,
            if (podDevice is HasChargeDetectionDual && podDevice.isLeftPodCharging) View.VISIBLE else View.GONE
        )
        setViewVisibility(
            R.id.pod_left_ear,
            if (podDevice is HasEarDetectionDual && podDevice.isLeftPodInEar) View.VISIBLE else View.GONE
        )

        // Case
        (podDevice as? HasCase)?.let { setImageViewResource(R.id.pod_case_icon, it.caseIcon) }
        val casePercent = (podDevice as? HasCase)?.batteryCasePercent
        setTextViewText(R.id.pod_case_label, formatBatteryPercent(context, casePercent))
        setViewVisibility(
            R.id.pod_case_charging,
            if (podDevice is HasCase && podDevice.isCaseCharging) View.VISIBLE else View.GONE
        )

        // Right
        val rightPercent = podDevice.batteryRightPodPercent
        setImageViewResource(R.id.pod_right_icon, podDevice.rightPodIcon)
        setTextViewText(R.id.pod_right_label, formatBatteryPercent(context, rightPercent))
        setViewVisibility(
            R.id.pod_right_charging,
            if (podDevice is HasChargeDetectionDual && podDevice.isRightPodCharging) View.VISIBLE else View.GONE
        )
        setViewVisibility(
            R.id.pod_right_ear,
            if (podDevice is HasEarDetectionDual && podDevice.isRightPodInEar) View.VISIBLE else View.GONE
        )

        applyThemeColors(
            context = context,
            views = this,
            theme = theme,
            textViewIds = listOf(
                R.id.headphones_label,
                R.id.pod_left_label,
                R.id.pod_right_label,
                R.id.pod_case_label,
            ),
            iconViewIds = listOf(
                R.id.pod_left_icon,
                R.id.pod_left_charging,
                R.id.pod_left_ear,
                R.id.pod_case_icon,
                R.id.pod_case_charging,
                R.id.pod_right_icon,
                R.id.pod_right_charging,
                R.id.pod_right_ear,
            ),
            hasDeviceLabel = true,
        )
    }

    private fun createSinglePodLayout(
        context: Context,
        podDevice: SinglePodDevice,
        widgetId: Int,
        theme: WidgetTheme,
        profileLabel: String?,
    ): RemoteViews = RemoteViews(context.packageName, R.layout.widget_pod_single_layout).apply {
        log(TAG, VERBOSE) { "createSinglePodLayout(context=$context, podDevice=$podDevice)" }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            widgetId,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        setOnClickPendingIntent(R.id.widget_root, pendingIntent)

        val headsetPercent = podDevice.batteryHeadsetPercent
        setTextViewText(R.id.headphones_label, profileLabel ?: podDevice.getLabel(context))
        setImageViewResource(R.id.headphones_icon, podDevice.iconRes)
        setImageViewResource(R.id.headphones_battery_icon, getBatteryDrawable(headsetPercent))
        setTextViewText(R.id.headphones_battery_label, formatBatteryPercent(context, headsetPercent))

        setViewVisibility(
            R.id.headphones_worn,
            if (podDevice is HasEarDetection && podDevice.isBeingWorn) View.VISIBLE else View.GONE
        )

        setViewVisibility(
            R.id.headphones_charging,
            if (podDevice is HasChargeDetectionDual && podDevice.isHeadsetBeingCharged) View.VISIBLE else View.GONE
        )

        applyThemeColors(
            context = context,
            views = this,
            theme = theme,
            textViewIds = listOf(
                R.id.headphones_label,
                R.id.headphones_battery_label,
            ),
            iconViewIds = listOf(
                R.id.headphones_icon,
                R.id.headphones_battery_icon,
                R.id.headphones_charging,
                R.id.headphones_worn,
            ),
            hasDeviceLabel = true,
        )
    }

    companion object {
        val TAG = logTag("Widget", "Provider")

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
            val intent = Intent(context, WidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(widgetId))
            }
            context.sendBroadcast(intent)
        }
    }
}
