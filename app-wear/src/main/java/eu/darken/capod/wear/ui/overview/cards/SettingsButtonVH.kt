package eu.darken.capod.wear.ui.overview.cards

import android.view.ViewGroup
import eu.darken.capod.R
import eu.darken.capod.common.lists.binding
import eu.darken.capod.common.lists.differ.DifferItem
import eu.darken.capod.databinding.OverviewSettingsItemBinding
import eu.darken.capod.wear.ui.overview.OverviewAdapter

class SettingsButtonVH(parent: ViewGroup) :
    OverviewAdapter.BaseVH<SettingsButtonVH.Item, OverviewSettingsItemBinding>(
        R.layout.overview_settings_item,
        parent
    ) {

    override val viewBinding = lazy {
        OverviewSettingsItemBinding.bind(itemView)
    }

    override val onBindData: OverviewSettingsItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding(payload = true) { item ->
        settingsButton.setOnClickListener { item.onClick() }
    }

    data class Item(
        val isEnabled: Boolean = true,
        val onClick: () -> Unit,
    ) : OverviewAdapter.Item {
        override val stableId: Long = Item::class.hashCode().toLong()

        override val payloadProvider: ((DifferItem, DifferItem) -> DifferItem?)
            get() = { old, new -> if (new::class.isInstance(old)) new else null }
    }
}