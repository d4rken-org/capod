package eu.darken.capod.main.ui.overview.cards.pods

import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import eu.darken.capod.R
import eu.darken.capod.common.lists.binding
import eu.darken.capod.databinding.OverviewPodsDualItemBinding
import eu.darken.capod.pods.core.DualPodDevice
import eu.darken.capod.pods.core.HasCase
import eu.darken.capod.pods.core.HasChargeDetectionDual
import eu.darken.capod.pods.core.HasDualMicrophone
import eu.darken.capod.pods.core.HasEarDetectionDual
import eu.darken.capod.pods.core.HasPodStyle
import eu.darken.capod.pods.core.HasStateDetection
import eu.darken.capod.pods.core.apple.ApplePods
import eu.darken.capod.pods.core.apple.DualApplePods
import eu.darken.capod.pods.core.apple.DualApplePods.LidState
import eu.darken.capod.pods.core.firstSeenFormatted
import eu.darken.capod.pods.core.getBatteryDrawable
import eu.darken.capod.pods.core.formatBatteryPercent
import eu.darken.capod.pods.core.lastSeenFormatted
import java.time.Duration
import java.time.Instant

class DualPodsCardVH(parent: ViewGroup) :
    PodDeviceVH<DualPodsCardVH.Item, OverviewPodsDualItemBinding>(
        R.layout.overview_pods_dual_item,
        parent
    ) {

    override val viewBinding = lazy { OverviewPodsDualItemBinding.bind(itemView) }

    override val onBindData = binding(payload = true) { item: Item ->
        val device = item.device

        name.text = device.meta.profile?.label ?: "?"

        deviceType.apply {
            val sb = StringBuilder(device.getLabel(context))
            if (device is HasPodStyle && item.showDebug) {
                val style = device.podStyle
                sb.append(" (${style.getColor(context)})")
            }
            text = sb

            if (device is DualApplePods && item.showDebug) {
                append(" [${device.primaryPod.name}]")
            }
        }
        deviceIcon.setImageResource(device.iconRes)
        podLeftIcon.setImageResource(device.leftPodIcon)
        podRightIcon.setImageResource(device.rightPodIcon)

        lastSeen.text =
            context.getString(R.string.last_seen_x, device.lastSeenFormatted(item.now))
        firstSeen.text =
            context.getString(R.string.first_seen_x, device.firstSeenFormatted(item.now))
        firstSeen.isGone = Duration.between(device.seenFirstAt, device.seenLastAt).toMinutes() < 1

        reception.text = item.getReceptionText()

        keyIcon.apply {
            isVisible = device is ApplePods && device.meta.isIRKMatch
            if (device !is ApplePods) return@apply
            setImageResource(
                when {
                    device.payload.private != null -> R.drawable.ic_key_24
                    else -> R.drawable.ic_key_outline_24
                }
            )
        }

        // Pods battery state
        device.apply {
            val leftPercent = batteryLeftPodPercent
            podLeftBatteryIcon.setImageResource(getBatteryDrawable(leftPercent))
            podLeftBatteryLabel.text = formatBatteryPercent(context, leftPercent)

            val rightPercent = batteryRightPodPercent
            podRightBatteryIcon.setImageResource(getBatteryDrawable(rightPercent))
            podRightBatteryLabel.text = formatBatteryPercent(context, rightPercent)
        }

        // Pods charging state
        device.apply {
            if (this is HasChargeDetectionDual) {
                podLeftChargingIcon.isInvisible = !isLeftPodCharging
                podLeftChargingLabel.isInvisible = !isLeftPodCharging

                podRightChargingIcon.isInvisible = !isRightPodCharging
                podRightChargingLabel.isInvisible = !isRightPodCharging
            } else {
                podLeftChargingIcon.isGone = true
                podLeftChargingLabel.isGone = true

                podRightChargingIcon.isGone = true
                podRightChargingLabel.isGone = true
            }
        }

        // Microphone state
        device.apply {
            if (this is HasDualMicrophone) {
                podLeftMicrophoneIcon.isInvisible = !isLeftPodMicrophone
                podLeftMicrophoneLabel.isInvisible = !isLeftPodMicrophone

                podRightMicrophoneIcon.isInvisible = !isRightPodMicrophone
                podRightMicrophoneLabel.isInvisible = !isRightPodMicrophone
            } else {
                podLeftMicrophoneIcon.isGone = true
                podLeftMicrophoneLabel.isGone = true

                podRightMicrophoneIcon.isGone = true
                podRightMicrophoneLabel.isGone = true
            }
        }

        // Pods wear state
        device.apply {
            if (this is HasEarDetectionDual) {
                podLeftWearIcon.isInvisible = !isLeftPodInEar
                podLeftWearLabel.isInvisible = !isLeftPodInEar

                podRightWearIcon.isInvisible = !isRightPodInEar
                podRightWearLabel.isInvisible = !isRightPodInEar
            } else {
                podLeftWearIcon.isGone = true
                podLeftWearLabel.isGone = true

                podRightWearIcon.isGone = true
                podRightWearLabel.isGone = true
            }
        }

        // Case charge state
        device.apply {
            if (this is HasCase) {
                podCaseIcon.setImageResource(caseIcon)
                podCaseBatteryIcon.isGone = false
                val casePercent = batteryCasePercent
                podCaseBatteryIcon.setImageResource(getBatteryDrawable(casePercent))
                podCaseBatteryLabel.text = formatBatteryPercent(context, casePercent)

                podCaseChargingIcon.isInvisible = !isCaseCharging
                podCaseChargingLabel.isInvisible = !isCaseCharging
            } else {
                podCaseBatteryIcon.isGone = true
                podCaseBatteryLabel.isGone = true

                podCaseChargingIcon.isGone = true
                podCaseChargingLabel.isGone = true
            }
        }

        // Case lid state
        device.apply {
            if (this is DualApplePods) {
                podCaseLidLabel.text = when (caseLidState) {
                    LidState.OPEN -> context.getString(R.string.pods_case_status_open_label)
                    LidState.CLOSED -> context.getString(R.string.pods_case_status_closed_label)
                    else -> context.getString(R.string.pods_case_unknown_state)
                }

                val hideInfo = !listOf(LidState.OPEN, LidState.CLOSED).contains(caseLidState)
                podCaseLidIcon.isInvisible = hideInfo
                podCaseLidLabel.isInvisible = hideInfo
            } else {
                podCaseLidIcon.isGone = true
                podCaseLidLabel.isGone = true
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
    ) : PodDeviceVH.Item
}