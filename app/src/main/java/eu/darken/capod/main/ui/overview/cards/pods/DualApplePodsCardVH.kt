package eu.darken.capod.main.ui.overview.cards.pods

import android.graphics.Typeface
import android.view.ViewGroup
import androidx.core.view.isInvisible
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

        // Left Pod
        device.apply {
            podLeftBatteryIcon.setImageResource(getBatteryDrawable(batteryLeftPodPercent))
            podLeftBatteryLabel.text = getBatteryLevelLeftPod(context)

            podLeftChargingIcon.isInvisible = !isLeftPodCharging
            podLeftChargingLabel.isInvisible = !isLeftPodCharging

            podLeftMicrophoneIcon.isInvisible = microPhonePod != Pod.LEFT
            podLeftMicrophoneLabel.isInvisible = microPhonePod != Pod.LEFT

            podLeftWearIcon.isInvisible = !isLeftPodInEar
            podLeftWearLabel.isInvisible = !isLeftPodInEar
        }

        // Right Pod
        device.apply {
            podRightBatteryIcon.setImageResource(getBatteryDrawable(batteryRightPodPercent))
            podRightBatteryLabel.text = getBatteryLevelRightPod(context)

            podRightChargingIcon.isInvisible = !isRightPodCharging
            podRightChargingLabel.isInvisible = !isRightPodCharging

            podRightMicrophoneIcon.isInvisible = microPhonePod != Pod.RIGHT
            podRightMicrophoneLabel.isInvisible = microPhonePod != Pod.RIGHT

            podRightWearIcon.isInvisible = !isRightPodInEar
            podRightWearLabel.isInvisible = !isRightPodInEar
        }

        // Case
        device.apply {
            podCaseBatteryIcon.setImageResource(getBatteryDrawable(batteryCasePercent))
            podCaseBatteryLabel.text = getBatteryLevelCase(context)

            podCaseChargingIcon.isInvisible = !isCaseCharging
            podCaseChargingLabel.isInvisible = !isCaseCharging

            podCaseLidLabel.text = when (device.caseLidState) {
                LidState.OPEN -> context.getString(R.string.pods_case_status_open_label)
                LidState.CLOSED -> context.getString(R.string.pods_case_status_closed_label)
                else -> context.getString(R.string.general_value_unknown_label)
            }
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