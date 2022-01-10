package eu.darken.capod.main.ui.overview.cards

import android.view.ViewGroup
import eu.darken.capod.R
import eu.darken.capod.common.lists.binding
import eu.darken.capod.common.lists.differ.DifferItem
import eu.darken.capod.databinding.OverviewNopaireddeviceItemBinding
import eu.darken.capod.main.ui.overview.OverviewAdapter

class NoPairedDeviceCardVH(parent: ViewGroup) :
    OverviewAdapter.BaseVH<NoPairedDeviceCardVH.Item, OverviewNopaireddeviceItemBinding>(
        R.layout.overview_nopaireddevice_item,
        parent
    ) {

    override val viewBinding = lazy {
        OverviewNopaireddeviceItemBinding.bind(itemView)
    }

    override val onBindData: OverviewNopaireddeviceItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding(payload = true) { item ->
        showAllAction.setOnClickListener { item.onShowAll() }
    }

    data class Item(
        val onShowAll: () -> Unit
    ) : OverviewAdapter.Item {
        override val stableId: Long = Item::class.hashCode().toLong()

        override val payloadProvider: ((DifferItem, DifferItem) -> DifferItem?)
            get() = { old, new -> if (new::class.isInstance(old)) new else null }
    }
}