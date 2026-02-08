package eu.darken.capod.monitor.ui

import android.content.Context
import android.view.View
import android.widget.RemoteViews
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.R
import eu.darken.capod.pods.core.DualPodDevice
import eu.darken.capod.pods.core.HasCase
import eu.darken.capod.pods.core.HasChargeDetectionDual
import eu.darken.capod.pods.core.HasEarDetection
import eu.darken.capod.pods.core.HasEarDetectionDual
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.SinglePodDevice
import eu.darken.capod.pods.core.formatBatteryPercent
import eu.darken.capod.pods.core.getBatteryDrawable
import javax.inject.Inject


class MonitorNotificationViewFactory @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun createContentView(device: PodDevice): RemoteViews = when (device) {
        is DualPodDevice -> createDualPods(device)
        is SinglePodDevice -> createSinglePod(device)
        else -> createUnknownDevice(device)
    }

    private fun createDualPods(device: DualPodDevice): RemoteViews = RemoteViews(
        context.packageName,
        R.layout.monitor_notification_dual_pods_small
    ).apply {
        // Left
        val leftPercent = device.batteryLeftPodPercent
        setImageViewResource(R.id.pod_left_icon, device.leftPodIcon)
        setTextViewText(R.id.pod_left_label, formatBatteryPercent(context, leftPercent))
        val isLeftPodCharging = (device as? HasChargeDetectionDual)?.isLeftPodCharging ?: false
        setViewVisibility(R.id.pod_left_charging, if (isLeftPodCharging) View.VISIBLE else View.GONE)
        val isLeftPodInEar = (device as? HasEarDetectionDual)?.isLeftPodInEar ?: false
        setViewVisibility(R.id.pod_left_ear, if (isLeftPodInEar) View.VISIBLE else View.GONE)

        // Case
        setViewVisibility(R.id.pod_case_charging, if (device is HasCase) View.VISIBLE else View.GONE)
        (device as? HasCase)?.let { case ->
            setImageViewResource(R.id.pod_case_icon, device.caseIcon)
            val casePercent = case.batteryCasePercent
            setTextViewText(R.id.pod_case_label, formatBatteryPercent(context, casePercent))
            setViewVisibility(R.id.pod_case_charging, if (case.isCaseCharging) View.VISIBLE else View.GONE)
        }

        // Right
        val rightPercent = device.batteryRightPodPercent
        setImageViewResource(R.id.pod_right_icon, device.rightPodIcon)
        setTextViewText(R.id.pod_right_label, formatBatteryPercent(context, rightPercent))
        val isRightPodCharging = (device as? HasChargeDetectionDual)?.isRightPodCharging ?: false
        setViewVisibility(R.id.pod_right_charging, if (isRightPodCharging) View.VISIBLE else View.GONE)
        val isRightPodInEar = (device as? HasEarDetectionDual)?.isRightPodInEar ?: false
        setViewVisibility(R.id.pod_right_ear, if (isRightPodInEar) View.VISIBLE else View.GONE)
    }

    private fun createSinglePod(device: SinglePodDevice): RemoteViews = RemoteViews(
        context.packageName,
        R.layout.monitor_notification_single_pods_small
    ).apply {
        val headsetPercent = device.batteryHeadsetPercent
        setTextViewText(R.id.headphones_label, device.getLabel(context))
        setImageViewResource(R.id.headphones_icon, device.iconRes)
        setImageViewResource(R.id.headphones_battery_icon, getBatteryDrawable(headsetPercent))
        setTextViewText(R.id.headphones_battery_label, formatBatteryPercent(context, headsetPercent))
        if (device is HasEarDetection) {
            setViewVisibility(R.id.headphones_worn, if (device.isBeingWorn) View.VISIBLE else View.GONE)
        }
        if (device is HasChargeDetectionDual) {
            setViewVisibility(R.id.headphones_charging, if (device.isHeadsetBeingCharged) View.VISIBLE else View.GONE)
        }
    }

    private fun createUnknownDevice(device: PodDevice): RemoteViews = RemoteViews(
        context.packageName,
        R.layout.monitor_notification_dual_pods_small
    ).apply {
        setTextViewText(R.id.device, device.getLabel(context))
    }

}