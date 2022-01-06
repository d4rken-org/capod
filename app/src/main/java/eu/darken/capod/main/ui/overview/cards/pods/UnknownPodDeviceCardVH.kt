package eu.darken.capod.main.ui.overview.cards.pods

import android.view.ViewGroup
import eu.darken.capod.R
import eu.darken.capod.common.lists.binding
import eu.darken.capod.databinding.OverviewPodsUnknownItemBinding
import eu.darken.capod.pods.core.PodDevice
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
        name.text = device.getLabel(context)

        lastSeen.text = device.lastSeenFormatted(item.now)

        reception.text = device.getSignalQuality(context)

        rawdata.text = device.rawDataHex
    }

    data class Item(
        override val now: Instant,
        override val device: PodDevice,
        override val showDebug: Boolean = false
    ) : PodDeviceVH.Item
}