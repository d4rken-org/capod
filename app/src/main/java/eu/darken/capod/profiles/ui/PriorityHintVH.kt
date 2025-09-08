package eu.darken.capod.profiles.ui

import android.view.ViewGroup
import eu.darken.capod.R
import eu.darken.capod.common.lists.BindableVH
import eu.darken.capod.databinding.DeviceManagerPriorityHintItemBinding

class PriorityHintVH(parent: ViewGroup) :
    DeviceManagerAdapter.BaseVH<PriorityHintVH.Item, DeviceManagerPriorityHintItemBinding>(
        R.layout.device_manager_priority_hint_item,
        parent
    ) {

    override val viewBinding = lazy { DeviceManagerPriorityHintItemBinding.bind(itemView) }

    override val onBindData: DeviceManagerPriorityHintItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = { _, _ ->
        // Nothing to bind - the layout already shows the static hint text
    }

    data class Item(
        val dummy: Unit = Unit
    ) : DeviceManagerAdapter.Item {
        override val stableId: Long = this.javaClass.hashCode().toLong()
    }
}