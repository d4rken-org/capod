package eu.darken.capod.main.ui.overview.cards.pods

import android.graphics.Typeface
import android.view.ViewGroup
import androidx.core.view.isInvisible
import eu.darken.capod.R
import eu.darken.capod.common.lists.binding
import eu.darken.capod.databinding.OverviewPodsAppleDualItemBinding
import eu.darken.capod.pods.core.*
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
            if (!listOf(DeviceColor.WHITE, DeviceColor.UNKNOWN).contains(device.deviceColor)) {
                sb.append(" (${device.deviceColor.name})")
            }
            text = sb
            if (item.isMainPod) setTypeface(typeface, Typeface.BOLD)
            else setTypeface(typeface, Typeface.NORMAL)
            if (item.showDebug) {
                append(" [${device.primaryPod.name}]")
            }
        }
        deviceIcon.setImageResource(device.iconRes)

        lastSeen.text = device.lastSeenFormatted(item.now)

        reception.text = device.getSignalQuality(context).let {
            if (item.isMainPod) "$it\n(${getString(R.string.pods_yours)})" else it
        }

        // Left Pod
        device.apply {
            podLeftBatteryIcon.setImageResource(getBatteryDrawable(batteryLeftPodPercent))
            podLeftBatteryLabel.text = getBatteryLevelLeftPod(context)

            podLeftChargingIcon.isInvisible = !isLeftPodCharging
            podLeftChargingLabel.isInvisible = !isLeftPodCharging

            podLeftMicrophoneIcon.isInvisible = !isLeftPodMicrophone
            podLeftMicrophoneLabel.isInvisible = !isLeftPodMicrophone

            podLeftWearIcon.isInvisible = !isLeftPodInEar
            podLeftWearLabel.isInvisible = !isLeftPodInEar
        }

        // Right Pod
        device.apply {
            podRightBatteryIcon.setImageResource(getBatteryDrawable(batteryRightPodPercent))
            podRightBatteryLabel.text = getBatteryLevelRightPod(context)

            podRightChargingIcon.isInvisible = !isRightPodCharging
            podRightChargingLabel.isInvisible = !isRightPodCharging

            podRightMicrophoneIcon.isInvisible = !isRightPodMicrophone
            podRightMicrophoneLabel.isInvisible = !isRightPodMicrophone

            podRightWearIcon.isInvisible = !isRightPodInEar
            podRightWearLabel.isInvisible = !isRightPodInEar
        }

        // Case
        device.apply {
            podCaseBatteryIcon.setImageResource(getBatteryDrawable(batteryCasePercent))
            podCaseBatteryLabel.text = getBatteryLevelCase(context)

            podCaseChargingIcon.isInvisible = !isCaseCharging
            podCaseChargingLabel.isInvisible = !isCaseCharging

            podCaseLidLabel.text = when (caseLidState) {
                LidState.OPEN -> context.getString(R.string.pods_case_status_open_label)
                LidState.CLOSED -> context.getString(R.string.pods_case_status_closed_label)
                else -> context.getString(R.string.general_value_unknown_label)
            }

            val hideInfo = !listOf(LidState.OPEN, LidState.CLOSED).contains(caseLidState)
            podCaseLidIcon.isInvisible = hideInfo
            podCaseLidLabel.isInvisible = hideInfo
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