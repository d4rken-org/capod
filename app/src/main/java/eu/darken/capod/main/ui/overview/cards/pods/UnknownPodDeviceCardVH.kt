package eu.darken.capod.main.ui.overview.cards.pods

import android.view.ViewGroup
import eu.darken.capod.R
import eu.darken.capod.common.lists.binding
import eu.darken.capod.databinding.OverviewPodsUnknownItemBinding
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.ApplePods
import eu.darken.capod.pods.core.lastSeenFormatted
import java.time.Instant

class UnknownPodDeviceCardVH(parent: ViewGroup) :
    PodDeviceVH<UnknownPodDeviceCardVH.Item, OverviewPodsUnknownItemBinding>(
        R.layout.overview_pods_unknown_item,
        parent
    ) {

    override val viewBinding = lazy {
        OverviewPodsUnknownItemBinding.bind(itemView)
    }

    override val onBindData = binding(payload = true) { item ->
        val device = item.device
        name.apply {
            text = device.getLabel(context)
        }

        lastSeen.text = device.lastSeenFormatted(item.now)
        reception.text = item.getReceptionText()

        details.text = when (item.device) {
            is ApplePods -> getString(R.string.pods_unknown_contact_dev)
            else -> getString(R.string.pods_unknown_label)
        }

        rawdata.text = device.rawDataHex.joinToString("\n")
    }

    data class Item(
        override val now: Instant,
        override val device: PodDevice,
        override val showDebug: Boolean = false,
    ) : PodDeviceVH.Item
}