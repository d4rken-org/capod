package eu.darken.capod.main.ui.overview.cards.pods

import android.icu.text.RelativeDateTimeFormatter
import android.view.ViewGroup
import androidx.core.view.isGone
import eu.darken.capod.R
import eu.darken.capod.common.lists.binding
import eu.darken.capod.databinding.OverviewPodsAppleSingleItemBinding
import eu.darken.capod.pods.core.apple.SingleApplePods
import eu.darken.capod.pods.core.getBatteryLevelHeadset
import java.time.Duration
import java.time.Instant

class SingleApplePodsCardVH(parent: ViewGroup) :
    PodDeviceVH<SingleApplePodsCardVH.Item, OverviewPodsAppleSingleItemBinding>(
        R.layout.overview_pods_apple_single_item,
        parent
    ) {

    override val viewBinding = lazy { OverviewPodsAppleSingleItemBinding.bind(itemView) }

    private val lastSeenFormatter = RelativeDateTimeFormatter.getInstance()

    override val onBindData = binding(payload = true) { item: Item ->
        val device = item.device

        name.text = device.getLabel(context)
        deviceIcon.setImageResource(device.iconRes)

        val duration = Duration.between(device.lastSeenAt, item.now)
        lastSeen.text = lastSeenFormatter.format(
            duration.seconds.toDouble(),
            RelativeDateTimeFormatter.Direction.LAST,
            RelativeDateTimeFormatter.RelativeUnit.SECONDS
        )

        reception.text = device.getSignalQuality(context)

        headphones.apply {
            val sb = StringBuilder(context.getString(R.string.pods_single_headphones_label))
            sb.append("\n").append(device.getBatteryLevelHeadset(context))
            when {
                device.isHeadsetBeingCharged -> sb.append("\n").append("Charging")
                device.isHeadphonesBeingWorn -> sb.append("\n").append("In ear")
                else -> {}
            }
            text = sb
        }

        status.apply {
            val sb = StringBuilder()
            if (item.showDebug) {
                sb.append("--- Debug ---")
                sb.append("\n").append(device.rawDataHex)
            }
            text = sb
            isGone = !item.showDebug
        }
    }

    data class Item(
        override val now: Instant,
        override val device: SingleApplePods,
        override val showDebug: Boolean,
    ) : PodDeviceVH.Item
}