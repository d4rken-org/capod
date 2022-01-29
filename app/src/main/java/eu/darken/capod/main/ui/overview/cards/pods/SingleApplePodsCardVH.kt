package eu.darken.capod.main.ui.overview.cards.pods

import android.graphics.Typeface
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import eu.darken.capod.R
import eu.darken.capod.common.lists.binding
import eu.darken.capod.databinding.OverviewPodsAppleSingleItemBinding
import eu.darken.capod.pods.core.apple.SingleApplePods
import eu.darken.capod.pods.core.getBatteryDrawable
import eu.darken.capod.pods.core.getBatteryLevelHeadset
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

        reception.text = item.getReceptionText()

        batteryLabel.text = device.getBatteryLevelHeadset(context)
        batteryIcon.setImageResource(getBatteryDrawable(device.batteryHeadsetPercent))

        chargingIcon.isInvisible = device.isHeadsetBeingCharged
        chargingLabel.isInvisible = device.isHeadsetBeingCharged

        wearIcon.isInvisible = device.isHeadphonesBeingWorn
        wearLabel.isInvisible = device.isHeadphonesBeingWorn

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