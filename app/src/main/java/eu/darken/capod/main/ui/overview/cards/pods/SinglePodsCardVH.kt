package eu.darken.capod.main.ui.overview.cards.pods

import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import eu.darken.capod.R
import eu.darken.capod.common.lists.binding
import eu.darken.capod.databinding.OverviewPodsSingleItemBinding
import eu.darken.capod.pods.core.HasChargeDetection
import eu.darken.capod.pods.core.HasEarDetection
import eu.darken.capod.pods.core.SinglePodDevice
import eu.darken.capod.pods.core.apple.ApplePods
import eu.darken.capod.pods.core.firstSeenFormatted
import eu.darken.capod.pods.core.getBatteryDrawable
import eu.darken.capod.pods.core.formatBatteryPercent
import eu.darken.capod.pods.core.lastSeenFormatted
import java.time.Duration
import java.time.Instant

class SinglePodsCardVH(parent: ViewGroup) :
    PodDeviceVH<SinglePodsCardVH.Item, OverviewPodsSingleItemBinding>(
        R.layout.overview_pods_single_item,
        parent
    ) {

    override val viewBinding = lazy { OverviewPodsSingleItemBinding.bind(itemView) }

    override val onBindData = binding(payload = true) { item: Item ->
        val device = item.device

        name.text = device.meta.profile?.label ?: "?"
        deviceType.text = device.getLabel(context)

        deviceIcon.setImageResource(device.iconRes)

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

        // Battery level
        device.apply {
            val headsetPercent = batteryHeadsetPercent
            batteryIcon.setImageResource(getBatteryDrawable(headsetPercent))
            batteryLabel.text = formatBatteryPercent(context, headsetPercent)
        }

        // Charge state
        device.apply {
            if (this is HasChargeDetection) {
                chargingIcon.isInvisible = !isHeadsetBeingCharged
                chargingLabel.isInvisible = !isHeadsetBeingCharged
            } else {
                chargingIcon.isGone = true
                chargingLabel.isGone = true
            }
        }

        // Has ear detection
        device.apply {
            if (this is HasEarDetection) {
                wearIcon.isInvisible = !isBeingWorn
                wearLabel.isInvisible = !isBeingWorn
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
    ) : PodDeviceVH.Item
}