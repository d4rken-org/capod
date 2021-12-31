package eu.darken.capod.main.ui.overview.cards

import android.view.ViewGroup
import eu.darken.capod.R
import eu.darken.capod.databinding.OverviewPodsUnknownItemBinding
import eu.darken.capod.main.ui.overview.OverviewAdapter
import eu.darken.capod.pods.core.PodDevice

class UnknownPodDeviceCardVH(parent: ViewGroup) :
    OverviewAdapter.BaseVH<UnknownPodDeviceCardVH.Item, OverviewPodsUnknownItemBinding>(
        R.layout.overview_pods_unknown_item,
        parent
    ) {

    override val viewBinding = lazy {
        OverviewPodsUnknownItemBinding.bind(itemView)
    }

    override val onBindData: OverviewPodsUnknownItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = { item, _ ->

        name.text = item.device.getLabel(context)
    }

    data class Item(
        val device: PodDevice
    ) : OverviewAdapter.Item {
        override val stableId: Long = this.javaClass.hashCode().toLong()
    }
}