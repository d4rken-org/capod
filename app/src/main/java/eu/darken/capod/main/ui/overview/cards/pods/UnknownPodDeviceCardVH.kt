package eu.darken.capod.main.ui.overview.cards.pods

import android.graphics.Typeface
import android.view.ViewGroup
import eu.darken.capod.R
import eu.darken.capod.common.lists.binding
import eu.darken.capod.databinding.OverviewPodsUnknownItemBinding
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.getSignalQuality
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
            if (item.isMainPod) setTypeface(typeface, Typeface.BOLD)
            else setTypeface(typeface, Typeface.NORMAL)
        }

        lastSeen.text = device.lastSeenFormatted(item.now)

        reception.text = device.getSignalQuality(context)
        if (item.isMainPod) {
            reception.append("\n(${getString(R.string.pods_yours)})")
        }

        rawdata.text = device.rawDataHex
    }

    data class Item(
        override val now: Instant,
        override val device: PodDevice,
        override val showDebug: Boolean = false,
        override val isMainPod: Boolean = false,
    ) : PodDeviceVH.Item
}