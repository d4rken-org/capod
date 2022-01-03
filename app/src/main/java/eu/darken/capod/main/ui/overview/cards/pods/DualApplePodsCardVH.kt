package eu.darken.capod.main.ui.overview.cards.pods

import android.icu.text.RelativeDateTimeFormatter
import android.view.ViewGroup
import eu.darken.capod.R
import eu.darken.capod.common.BuildConfigWrap
import eu.darken.capod.databinding.OverviewPodsAppleDualItemBinding
import eu.darken.capod.pods.core.DualPods
import eu.darken.capod.pods.core.airpods.DualApplePods
import eu.darken.capod.pods.core.airpods.DualApplePods.DeviceColor
import eu.darken.capod.pods.core.airpods.DualApplePods.LidState
import eu.darken.capod.pods.core.getBatteryCase
import eu.darken.capod.pods.core.getBatteryLeftPod
import eu.darken.capod.pods.core.getBatteryRightPod
import java.time.Duration
import java.time.Instant

class DualApplePodsCardVH(parent: ViewGroup) :
    PodDeviceVH<DualApplePodsCardVH.Item, OverviewPodsAppleDualItemBinding>(
        R.layout.overview_pods_apple_dual_item,
        parent
    ) {

    override val viewBinding = lazy {
        OverviewPodsAppleDualItemBinding.bind(itemView)
    }

    private val lastSeenFormatter = RelativeDateTimeFormatter.getInstance()

    override val onBindData: OverviewPodsAppleDualItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = { item, _ ->
        val device = item.device


        name.apply {
            val sb = StringBuilder(device.getLabel(context))
            if (device.deviceColor != DeviceColor.UNKNOWN) {
                sb.append(" (${device.getDeviceColorLabel(context)})")
            }
            text = sb
        }
        deviceIcon.setImageResource(device.iconRes)

        val duration = Duration.between(device.lastSeenAt, Instant.now())
        lastSeen.text = lastSeenFormatter.format(
            duration.seconds.toDouble(),
            RelativeDateTimeFormatter.Direction.LAST,
            RelativeDateTimeFormatter.RelativeUnit.SECONDS
        )

        reception.text = device.getSignalQuality(context)

        podLeft.apply {
            val sb = StringBuilder(context.getString(R.string.pods_dual_left_label))
            sb.append("\n").append(device.getBatteryLeftPod(context))
            when {
                device.isLeftPodCharging -> sb.append("\n").append("Charging")
                device.isLeftPodInEar -> sb.append("\n").append("In ear")
                else -> {}
            }
            text = sb
        }

        podRight.apply {
            val sb = StringBuilder("Right pod")
            sb.append("\n").append(device.getBatteryRightPod(context))
            when {
                device.isRightPodCharging -> sb.append("\n").append("Charging")
                device.isRightPodInEar -> sb.append("\n").append("In ear")
                else -> {}
            }
            text = sb
        }

        when (device.microPhonePod) {
            DualPods.Pod.LEFT -> podLeft.append("\n(Microphone)")
            DualPods.Pod.RIGHT -> podRight.append("\n(Microphone)")
        }

        podCase.apply {
            val sb = StringBuilder("Case")
            sb.append("\n").append(device.getBatteryCase(context))
            if (device.isCaseCharging) sb.append("\n").append("Charging")
            when (device.caseLidState) {
                LidState.OPEN -> sb.append("\n").append(context.getString(R.string.pods_case_status_open_label))
                LidState.CLOSED -> sb.append("\n").append(context.getString(R.string.pods_case_status_closed_label))
                LidState.NOT_IN_CASE,
                LidState.UNKNOWN -> {
                }
            }
            text = sb
        }

        status.text = getString(R.string.pods_status_x_label, device.getConnectionStateLabel(context))
        if (BuildConfigWrap.DEBUG) {
            status.append("\n")
            status.append(device.identifier.toString())
        }
    }

    data class Item(
        override val device: DualApplePods
    ) : PodDeviceVH.Item
}