package eu.darken.capod.main.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
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
import eu.darken.capod.pods.core.*
import finish2
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

    private suspend fun updateWidget(
        context: Context,
        widgetManager: AppWidgetManager,
        widgetId: Int,
        options: Bundle?
    ) {
        log(TAG) { "updateWidget(widgetId=$widgetId, options=$options)" }

        val device: PodDevice? = podMonitor.latestMainDevice()

        val layout = when {
            !upgradeRepo.isPro() -> createUpgradeRequiredLayout(context)
            device is DualPodDevice -> createDualPodLayout(context, device)
            device is SinglePodDevice -> createSinglePodLayout(context, device)
            device is PodDevice -> createUnknownPodLayout(context, device)
            else -> createNoDeviceLayout(context)
        }
        widgetManager.updateAppWidget(widgetId, layout)
    }

    private suspend fun createUpgradeRequiredLayout(
        context: Context
    ) = RemoteViews(context.packageName, R.layout.widget_message_layout).apply {
        log(TAG, VERBOSE) { "createUpgradeRequiredLayout(context=$context)" }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        setOnClickPendingIntent(R.id.widget_root, pendingIntent)

        setTextViewText(R.id.primary, context.getString(R.string.upgrade_capod_label))
        setTextViewText(R.id.secondary, context.getString(R.string.upgrade_capod_description))
        setViewVisibility(R.id.secondary, View.VISIBLE)
    }

    private fun createUnknownPodLayout(
        context: Context,
        podDevice: PodDevice,
    ): RemoteViews = RemoteViews(context.packageName, R.layout.widget_message_layout).apply {
        log(TAG, VERBOSE) { "createUnknownPodLayout(context=$context, podDevice=$podDevice)" }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        setOnClickPendingIntent(R.id.widget_root, pendingIntent)

        setTextViewText(R.id.primary, context.getString(R.string.pods_unknown_label))
    }

    private fun createNoDeviceLayout(
        context: Context,
    ): RemoteViews = RemoteViews(context.packageName, R.layout.widget_message_layout).apply {
        log(TAG, VERBOSE) { "createNoDeviceLayout(context=$context)" }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        setOnClickPendingIntent(R.id.widget_root, pendingIntent)

        setTextViewText(R.id.primary, context.getString(R.string.overview_nomaindevice_label))
    }

    private fun createDualPodLayout(
        context: Context,
        podDevice: DualPodDevice,
    ): RemoteViews = RemoteViews(context.packageName, R.layout.widget_pod_dual_layout).apply {
        log(TAG, VERBOSE) { "createSinglePodLayout(context=$context, podDevice=$podDevice)" }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        setOnClickPendingIntent(R.id.widget_root, pendingIntent)

        setTextViewText(R.id.headphones_label, podDevice.getLabel(context))

        // Left
        setImageViewResource(R.id.pod_left_icon, podDevice.leftPodIcon)
        setTextViewText(R.id.pod_left_label, podDevice.getBatteryLevelLeftPod(context))
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
        setTextViewText(R.id.pod_case_label, (podDevice as? HasCase)?.getBatteryLevelCase(context))
        setViewVisibility(
            R.id.pod_case_charging,
            if (podDevice is HasCase && podDevice.isCaseCharging) View.VISIBLE else View.GONE
        )

        // Right
        setImageViewResource(R.id.pod_right_icon, podDevice.rightPodIcon)
        setTextViewText(R.id.pod_right_label, podDevice.getBatteryLevelRightPod(context))
        setViewVisibility(
            R.id.pod_right_charging,
            if (podDevice is HasChargeDetectionDual && podDevice.isRightPodCharging) View.VISIBLE else View.GONE
        )
        setViewVisibility(
            R.id.pod_right_ear,
            if (podDevice is HasEarDetectionDual && podDevice.isRightPodInEar) View.VISIBLE else View.GONE
        )
    }

    private fun createSinglePodLayout(
        context: Context,
        podDevice: SinglePodDevice,
    ): RemoteViews = RemoteViews(context.packageName, R.layout.widget_pod_single_layout).apply {
        log(TAG, VERBOSE) { "createSinglePodLayout(context=$context, podDevice=$podDevice)" }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        setOnClickPendingIntent(R.id.widget_root, pendingIntent)

        setTextViewText(R.id.headphones_label, podDevice.getLabel(context))
        setImageViewResource(R.id.headphones_icon, podDevice.iconRes)
        setImageViewResource(R.id.headphones_battery_icon, getBatteryDrawable(podDevice.batteryHeadsetPercent))
        setTextViewText(R.id.headphones_battery_label, podDevice.getBatteryLevelHeadset(context))

        setViewVisibility(
            R.id.headphones_worn,
            if (podDevice is HasEarDetection && podDevice.isBeingWorn) View.VISIBLE else View.GONE
        )

        if (this is HasChargeDetectionDual) {
            setViewVisibility(R.id.headphones_charging, if (isHeadsetBeingCharged) View.VISIBLE else View.GONE)
        }
    }

    companion object {
        val TAG = logTag("Widget", "Provider")
    }
}