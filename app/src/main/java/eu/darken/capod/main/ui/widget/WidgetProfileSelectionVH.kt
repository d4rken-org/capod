package eu.darken.capod.main.ui.widget

import android.view.ViewGroup
import eu.darken.capod.R
import eu.darken.capod.common.lists.binding
import eu.darken.capod.databinding.WidgetConfigurationProfileItemBinding
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.profiles.core.DeviceProfile

class WidgetProfileSelectionVH(parent: ViewGroup) :
    WidgetProfileSelectionAdapter.BaseVH<WidgetProfileSelectionVH.Item, WidgetConfigurationProfileItemBinding>(
        R.layout.widget_configuration_profile_item,
        parent
    ) {

    override val viewBinding = lazy {
        WidgetConfigurationProfileItemBinding.bind(itemView)
    }

    override val onBindData: WidgetConfigurationProfileItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding(payload = true) { item ->
        val profile = item.profile

        profileName.text = profile.label
        val modelText = when (profile.model) {
            PodDevice.Model.UNKNOWN -> context.getString(R.string.pods_unknown_label)
            else -> profile.model.label
        }
        profileModel.text = modelText
        profileIcon.setImageResource(profile.model.iconRes)
        profileRadio.isChecked = item.isSelected
        root.isChecked = item.isSelected

        root.setOnClickListener { item.onProfileClick(profile) }
    }

    data class Item(
        val profile: DeviceProfile,
        val isSelected: Boolean,
        val onProfileClick: (DeviceProfile) -> Unit,
    ) : WidgetProfileSelectionAdapter.Item {
        override val stableId: Long = profile.id.hashCode().toLong()
    }
}