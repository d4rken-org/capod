package eu.darken.capod.main.ui.overview.cards

import android.view.ViewGroup
import eu.darken.capod.R
import eu.darken.capod.common.lists.binding
import eu.darken.capod.common.lists.differ.DifferItem
import eu.darken.capod.databinding.OverviewNomaindeviceItemBinding
import eu.darken.capod.main.ui.overview.OverviewAdapter

class MissingMainDeviceVH(parent: ViewGroup) :
    OverviewAdapter.BaseVH<MissingMainDeviceVH.Item, OverviewNomaindeviceItemBinding>(
        R.layout.overview_nomaindevice_item,
        parent
    ) {

    override val viewBinding = lazy {
        OverviewNomaindeviceItemBinding.bind(itemView)
    }

    override val onBindData: OverviewNomaindeviceItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding(payload = true) { item ->
        manageDevicesAction.setOnClickListener { item.onManageDevices() }
    }

    data class Item(
        val onManageDevices: () -> Unit,
    ) : OverviewAdapter.Item {
        override val stableId: Long = Item::class.hashCode().toLong()

        override val payloadProvider: ((DifferItem, DifferItem) -> DifferItem?)
            get() = { old, new -> if (new::class.isInstance(old)) new else null }
    }
}