package eu.darken.capod.main.ui.overview.cards.pods

import android.graphics.Typeface
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import eu.darken.capod.R
import eu.darken.capod.common.lists.binding
import eu.darken.capod.databinding.OverviewPodsSingleItemBinding
import eu.darken.capod.pods.core.*
import java.time.Instant

class SinglePodsCardVH(parent: ViewGroup) :
    PodDeviceVH<SinglePodsCardVH.Item, OverviewPodsSingleItemBinding>(
        R.layout.overview_pods_single_item,
        parent
    ) {

    override val viewBinding = lazy { OverviewPodsSingleItemBinding.bind(itemView) }

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

        // Battery level
        device.apply {
            batteryLabel.text = getBatteryLevelHeadset(context)
            batteryIcon.setImageResource(getBatteryDrawable(batteryHeadsetPercent))
        }

        // Charge state
        device.apply {
            if (this is HasChargeDetection) {
                chargingIcon.isInvisible = isHeadsetBeingCharged
                chargingLabel.isInvisible = isHeadsetBeingCharged
            } else {
                chargingIcon.isGone = true
                chargingLabel.isGone = true
            }
        }

        // Has ear detection
        device.apply {
            if (this is HasEarDetection) {
                wearIcon.isInvisible = isBeingWorn
                wearLabel.isInvisible = isBeingWorn
            } else {
                wearIcon.isGone = true
                wearLabel.isGone = true
            }
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
        override val device: SinglePodDevice,
        override val showDebug: Boolean,
        override val isMainPod: Boolean,
    ) : PodDeviceVH.Item
}