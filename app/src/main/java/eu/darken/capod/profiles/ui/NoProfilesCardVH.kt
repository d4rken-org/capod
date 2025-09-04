package eu.darken.capod.profiles.ui

import android.view.ViewGroup
import eu.darken.capod.R
import eu.darken.capod.common.lists.binding
import eu.darken.capod.common.lists.differ.DifferItem
import eu.darken.capod.databinding.DeviceManagerNoprofilesItemBinding

class NoProfilesCardVH(parent: ViewGroup) :
    DeviceManagerAdapter.BaseVH<NoProfilesCardVH.Item, DeviceManagerNoprofilesItemBinding>(
        R.layout.device_manager_noprofiles_item,
        parent
    ) {

    override val viewBinding = lazy {
        DeviceManagerNoprofilesItemBinding.bind(itemView)
    }

    override val onBindData: DeviceManagerNoprofilesItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding(payload = true) { item ->
        addProfileAction.setOnClickListener { item.onAddProfile() }
    }

    data class Item(
        val onAddProfile: () -> Unit,
    ) : DeviceManagerAdapter.Item {
        override val stableId: Long = Item::class.hashCode().toLong()

        override val payloadProvider: ((DifferItem, DifferItem) -> DifferItem?)
            get() = { old, new -> if (new::class.isInstance(old)) new else null }
    }
}