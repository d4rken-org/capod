package eu.darken.capod.wear.ui.overview.cards.pods

import android.graphics.Typeface
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import eu.darken.capod.R
import eu.darken.capod.common.lists.binding
import eu.darken.capod.databinding.OverviewPodsDualItemBinding
import eu.darken.capod.pods.core.*
import eu.darken.capod.pods.core.apple.DualApplePods
import java.time.Instant

class DualPodsCardVH(parent: ViewGroup) :
    PodDeviceVH<DualPodsCardVH.Item, OverviewPodsDualItemBinding>(
        R.layout.overview_pods_dual_item,
        parent
    ) {

    override val viewBinding = lazy { OverviewPodsDualItemBinding.bind(itemView) }

    override val onBindData = binding(payload = true) { item: Item ->
        val device = item.device
        name.apply {
            val sb = StringBuilder(device.getLabel(context))
            if (device is HasPodStyle && item.showDebug) {
                val style = device.podStyle
                sb.append(" (${style.getColor(context)})")
            }
            text = sb

            if (item.isMainPod) setTypeface(null, Typeface.BOLD)
            else setTypeface(null, Typeface.NORMAL)

            if (device is DualApplePods && item.showDebug) {
                append(" [${device.primaryPod.name}]")
            }
        }
        deviceIcon.setImageResource(device.iconRes)
        podLeftIcon.setImageResource(device.leftPodIcon)
        podRightIcon.setImageResource(device.rightPodIcon)

        lastSeen.text = device.lastSeenFormatted(item.now)

        // Pods battery state
        device.apply {
            podLeftBatteryIcon.setImageResource(getBatteryDrawable(batteryLeftPodPercent))
            podLeftBatteryLabel.text = getBatteryLevelLeftPod(context)

            podRightBatteryIcon.setImageResource(getBatteryDrawable(batteryRightPodPercent))
            podRightBatteryLabel.text = getBatteryLevelRightPod(context)
        }

        // Pods charging state
        device.apply {
            if (this is HasChargeDetectionDual) {
                podLeftChargingIcon.isInvisible = !isLeftPodCharging

                podRightChargingIcon.isInvisible = !isRightPodCharging
            } else {
                podLeftChargingIcon.isGone = true

                podRightChargingIcon.isGone = true
            }
        }

        // Microphone state
        device.apply {
            if (this is HasDualMicrophone) {
                podLeftMicrophoneIcon.isInvisible = !isLeftPodMicrophone

                podRightMicrophoneIcon.isInvisible = !isRightPodMicrophone
            } else {
                podLeftMicrophoneIcon.isGone = true

                podRightMicrophoneIcon.isGone = true
            }
        }

        // Pods wear state
        device.apply {
            if (this is HasEarDetectionDual) {
                podLeftWearIcon.isInvisible = !isLeftPodInEar

                podRightWearIcon.isInvisible = !isRightPodInEar
            } else {
                podLeftWearIcon.isGone = true

                podRightWearIcon.isGone = true
            }
        }

        // Case charge state
        device.apply {
            if (this is HasCase) {
                podCaseIcon.setImageResource(caseIcon)
                podCaseBatteryIcon.isGone = false
                podCaseBatteryIcon.setImageResource(getBatteryDrawable(batteryCasePercent))
                podCaseBatteryLabel.text = getBatteryLevelCase(context)

                podCaseChargingIcon.isInvisible = !isCaseCharging
            } else {
                podCaseBatteryIcon.isGone = true
                podCaseBatteryLabel.isGone = true

                podCaseChargingIcon.isGone = true
            }
        }

        // Connection state
        device.apply {
            val sb = StringBuilder()
            if (this is HasStateDetection) {
                sb.append(state.getLabel(context))
            }
            if (item.showDebug) {
                sb.append("\n\n").append("---Debug---")
                sb.append("\n").append(rawDataHex)
            }
            status.text = sb
            status.isGone = sb.isEmpty()
        }
    }

    data class Item(
        override val now: Instant,
        override val device: DualPodDevice,
        override val showDebug: Boolean,
        override val isMainPod: Boolean,
    ) : PodDeviceVH.Item
}