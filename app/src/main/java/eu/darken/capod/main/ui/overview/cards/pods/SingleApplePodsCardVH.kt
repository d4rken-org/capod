package eu.darken.capod.main.ui.overview.cards.pods

import android.graphics.Typeface
import android.view.ViewGroup
import androidx.core.view.isGone
import eu.darken.capod.R
import eu.darken.capod.common.lists.binding
import eu.darken.capod.databinding.OverviewPodsAppleSingleItemBinding
import eu.darken.capod.pods.core.apple.SingleApplePods
import eu.darken.capod.pods.core.getBatteryLevelHeadset
import eu.darken.capod.pods.core.getSignalQuality
import eu.darken.capod.pods.core.lastSeenFormatted
import java.time.Instant

class SingleApplePodsCardVH(parent: ViewGroup) :
    PodDeviceVH<SingleApplePodsCardVH.Item, OverviewPodsAppleSingleItemBinding>(
        R.layout.overview_pods_apple_single_item,
        parent
    ) {

    override val viewBinding = lazy { OverviewPodsAppleSingleItemBinding.bind(itemView) }

    override val onBindData = binding(payload = true) { item: Item ->
        val device = item.device

        name.apply {
            text = device.getLabel(context)
            if (item.isMainPod) setTypeface(typeface, Typeface.BOLD)
            else setTypeface(typeface, Typeface.NORMAL)
        }

        deviceIcon.setImageResource(device.iconRes)

        lastSeen.text = device.lastSeenFormatted(item.now)

        reception.text = device.getSignalQuality(context)
        if (item.isMainPod) {
            reception.append("\n(${getString(R.string.pods_yours)})")
        }

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
        override val isMainPod: Boolean,
    ) : PodDeviceVH.Item
}