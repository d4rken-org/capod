package eu.darken.capod.monitor.ui

import android.content.Context
import android.widget.RemoteViews
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.R
import eu.darken.capod.pods.core.*
import eu.darken.capod.pods.core.apple.BasicSingleApplePods
import eu.darken.capod.pods.core.apple.DualApplePods
import eu.darken.capod.pods.core.apple.SingleApplePods
import javax.inject.Inject


class MonitorNotificationViewFactory @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun createContentView(device: PodDevice): RemoteViews = when (device) {
        is DualApplePods -> createDualApplePods(device)
        is SingleApplePods -> createSingleApplePods(device)
        is BasicSingleApplePods -> createSingleBasicApplePods(device)
        else -> createUnknownDevice(device)
    }

    private fun createDualApplePods(device: DualApplePods): RemoteViews = RemoteViews(
        context.packageName,
        R.layout.monitor_notification_dual_pods_small
    ).apply {
        device.getBatteryLevelLeftPod(context)
            .let { if (device.isLeftPodCharging) "$it⚡" else it }
            .run { setTextViewText(R.id.pod_left, this) }

        device.getBatteryLevelCase(context)
            .let { if (device.isCaseCharging) "$it⚡" else it }
            .run { setTextViewText(R.id.pod_case, this) }

        device.getBatteryLevelRightPod(context)
            .let { if (device.isRightPodCharging) "$it⚡" else it }
            .run { setTextViewText(R.id.pod_right, this) }
    }

    private fun createSingleApplePods(device: SingleApplePods): RemoteViews = RemoteViews(
        context.packageName,
        R.layout.monitor_notification_single_pods_small
    ).apply {
        setTextViewText(R.id.headphones_label, device.getLabel(context))

        device.getBatteryLevelHeadset(context)
            .let { if (!device.isHeadsetBeingCharged) "$it⚡" else it }
            .run { setTextViewText(R.id.headphones, this) }
    }

    private fun createSingleBasicApplePods(device: BasicSingleApplePods): RemoteViews = RemoteViews(
        context.packageName,
        R.layout.monitor_notification_single_pods_basic_small
    ).apply {
        setTextViewText(R.id.headphones_label, device.getLabel(context))

        device.getBatteryLevelHeadset(context)
            .run { setTextViewText(R.id.headphones, this) }
    }

    private fun createUnknownDevice(device: PodDevice): RemoteViews = RemoteViews(
        context.packageName,
        R.layout.monitor_notification_dual_pods_small
    ).apply {
        setTextViewText(R.id.device, device.getLabel(context))
    }

}