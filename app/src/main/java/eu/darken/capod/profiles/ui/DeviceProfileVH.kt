package eu.darken.capod.profiles.ui

import android.view.ViewGroup
import eu.darken.capod.R
import eu.darken.capod.databinding.DeviceManagerItemBinding
import eu.darken.capod.profiles.core.DeviceProfile

class DeviceProfileVH(parent: ViewGroup) :
    DeviceManagerAdapter.BaseVH<DeviceProfileVH.Item, DeviceManagerItemBinding>(
        R.layout.device_manager_item,
        parent
    ) {

    override val viewBinding = lazy { DeviceManagerItemBinding.bind(itemView) }

    override val onBindData: DeviceManagerItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = { item, _ ->
        val profile = item.profile

        deviceIcon.setImageResource(profile.model.iconRes)
        deviceName.text = profile.label
        deviceDetails.text = buildString {
            profile.address?.let {
                append(it.toString())
                append(" • ")
            }
            append(profile.model.name)
        }

        itemView.setOnClickListener { item.onItemClick(profile) }
    }

    data class Item(
        val profile: DeviceProfile,
        val onItemClick: (DeviceProfile) -> Unit,
    ) : DeviceManagerAdapter.Item {
        override val stableId: Long = profile.id.hashCode().toLong()
    }
}