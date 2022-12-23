package eu.darken.capod.monitor.ui

import android.content.Context
import android.view.View
import android.widget.RemoteViews
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.R
import eu.darken.capod.pods.core.*
import eu.darken.capod.pods.core.apple.DualAirPods
import eu.darken.capod.pods.core.apple.SingleApplePods
import javax.inject.Inject


class MonitorNotificationViewFactory @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun createContentView(device: PodDevice): RemoteViews = when (device) {
        is DualAirPods -> createDualApplePods(device)
        is SingleApplePods -> createSingleApplePods(device)
        else -> createUnknownDevice(device)
    }

    private fun createDualApplePods(device: DualAirPods): RemoteViews = RemoteViews(
        context.packageName,
        R.layout.monitor_notification_dual_pods_small
    ).apply {
        device.apply {
            // Left
            setImageViewResource(R.id.pod_left_icon, device.leftPodIcon)
            setTextViewText(R.id.pod_left_label, getBatteryLevelLeftPod(context))
            setViewVisibility(R.id.pod_left_charging, if (isLeftPodCharging) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.pod_left_ear, if (isLeftPodInEar) View.VISIBLE else View.GONE)

            // Case
            setImageViewResource(R.id.pod_case_icon, device.caseIcon)
            setTextViewText(R.id.pod_case_label, getBatteryLevelCase(context))
            setViewVisibility(R.id.pod_case_charging, if (isCaseCharging) View.VISIBLE else View.GONE)

            // Right
            setImageViewResource(R.id.pod_right_icon, device.rightPodIcon)
            setTextViewText(R.id.pod_right_label, getBatteryLevelRightPod(context))
            setViewVisibility(R.id.pod_right_charging, if (isRightPodCharging) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.pod_right_ear, if (isRightPodInEar) View.VISIBLE else View.GONE)
        }
    }

    private fun createSingleApplePods(device: SingleApplePods): RemoteViews = RemoteViews(
        context.packageName,
        R.layout.monitor_notification_single_pods_small
    ).apply {
        device.apply {
            setTextViewText(R.id.headphones_label, getLabel(context))
            setImageViewResource(R.id.headphones_icon, device.iconRes)
            setImageViewResource(R.id.headphones_battery_icon, getBatteryDrawable(batteryHeadsetPercent))
            setTextViewText(R.id.headphones_battery_label, getBatteryLevelHeadset(context))
            if (this is HasEarDetection) {
                setViewVisibility(R.id.headphones_worn, if (isBeingWorn) View.VISIBLE else View.GONE)
            }
            if (this is HasChargeDetectionDual) {
                setViewVisibility(R.id.headphones_charging, if (isHeadsetBeingCharged) View.VISIBLE else View.GONE)
            }
        }
    }

    private fun createUnknownDevice(device: PodDevice): RemoteViews = RemoteViews(
        context.packageName,
        R.layout.monitor_notification_dual_pods_small
    ).apply {
        setTextViewText(R.id.device, device.getLabel(context))
    }

}