package eu.darken.capod.main.ui.overview.cards.pods

import android.graphics.Typeface
import android.view.ViewGroup
import eu.darken.capod.R
import eu.darken.capod.common.lists.binding
import eu.darken.capod.databinding.OverviewPodsAppleDualItemBinding
import eu.darken.capod.pods.core.*
import eu.darken.capod.pods.core.HasDualPods.Pod
import eu.darken.capod.pods.core.apple.DualApplePods
import eu.darken.capod.pods.core.apple.DualApplePods.DeviceColor
import eu.darken.capod.pods.core.apple.DualApplePods.LidState
import java.time.Instant

class DualApplePodsCardVH(parent: ViewGroup) :
    PodDeviceVH<DualApplePodsCardVH.Item, OverviewPodsAppleDualItemBinding>(
        R.layout.overview_pods_apple_dual_item,
        parent
    ) {

    override val viewBinding = lazy { OverviewPodsAppleDualItemBinding.bind(itemView) }

    override val onBindData = binding(payload = true) { item: Item ->
        val device = item.device
        name.apply {
            val sb = StringBuilder(device.getLabel(context))
            if (device.deviceColor != DeviceColor.UNKNOWN) {
                sb.append(" (${device.getDeviceColorLabel(context)})")
            }
            text = sb
            if (item.isMainPod) setTypeface(typeface, Typeface.BOLD)
            else setTypeface(typeface, Typeface.NORMAL)
        }
        deviceIcon.setImageResource(device.iconRes)

        lastSeen.text = device.lastSeenFormatted(item.now)

        reception.text = device.getSignalQuality(context)
        if (item.isMainPod) {
            reception.append("\n(${getString(R.string.pods_yours)})")
        }

        podLeft.apply {
            val sb = StringBuilder(context.getString(R.string.pods_dual_left_label))
            sb.append("\n").append(device.getBatteryLevelLeftPod(context))
            when {
                device.isLeftPodCharging -> sb.append("\n").append(getString(R.string.pods_charging_label))
                device.isLeftPodInEar -> sb.append("\n").append(getString(R.string.pods_inear_label))
                else -> {}
            }
            text = sb
        }

        podRight.apply {
            val sb = StringBuilder(getString(R.string.pods_dual_right_label))
            sb.append("\n").append(device.getBatteryLevelRightPod(context))
            when {
                device.isRightPodCharging -> sb.append("\n").append(getString(R.string.pods_charging_label))
                device.isRightPodInEar -> sb.append("\n").append(getString(R.string.pods_inear_label))
                else -> {}
            }
            text = sb
        }

        when (device.microPhonePod) {
            Pod.LEFT -> podLeft.append("\n(${context.getString(R.string.pods_microphone_label)})")
            Pod.RIGHT -> podRight.append("\n(${context.getString(R.string.pods_microphone_label)})")
        }

        podCase.apply {
            val sb = StringBuilder(getString(R.string.pods_case_label))
            sb.append("\n").append(device.getBatteryLevelCase(context))
            if (device.isCaseCharging) sb.append("\n").append(getString(R.string.pods_charging_label))
            when (device.caseLidState) {
                LidState.OPEN -> sb.append("\n").append(context.getString(R.string.pods_case_status_open_label))
                LidState.CLOSED -> sb.append("\n").append(context.getString(R.string.pods_case_status_closed_label))
                LidState.NOT_IN_CASE,
                LidState.UNKNOWN -> {
                }
            }
            text = sb
        }

        status.apply {
            val sb = StringBuilder(device.getConnectionStateLabel(context))
            if (item.showDebug) {
                sb.append("\n\n").append("---Debug---")
                sb.append("\n").append(device.rawDataHex)
            }
            text = sb
        }
    }

    data class Item(
        override val now: Instant,
        override val device: DualApplePods,
        override val showDebug: Boolean,
        override val isMainPod: Boolean,
    ) : PodDeviceVH.Item
}