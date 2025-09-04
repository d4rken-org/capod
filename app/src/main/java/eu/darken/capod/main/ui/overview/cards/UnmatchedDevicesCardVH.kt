package eu.darken.capod.main.ui.overview.cards

import android.view.ViewGroup
import eu.darken.capod.R
import eu.darken.capod.common.lists.binding
import eu.darken.capod.common.lists.differ.DifferItem
import eu.darken.capod.databinding.OverviewUnmatchedDevicesItemBinding
import eu.darken.capod.main.ui.overview.OverviewAdapter

class UnmatchedDevicesCardVH(parent: ViewGroup) :
    OverviewAdapter.BaseVH<UnmatchedDevicesCardVH.Item, OverviewUnmatchedDevicesItemBinding>(
        R.layout.overview_unmatched_devices_item,
        parent
    ) {

    override val viewBinding = lazy {
        OverviewUnmatchedDevicesItemBinding.bind(itemView)
    }

    override val onBindData: OverviewUnmatchedDevicesItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding(payload = true) { item ->
        val countText = when (item.count) {
            1 -> context.getString(R.string.overview_unmatched_devices_count_single)
            else -> context.getString(R.string.overview_unmatched_devices_count_plural, item.count)
        }
        unmatchedCount.text = countText
        
        val toggleText = if (item.isExpanded) {
            context.getString(R.string.general_hide_action)
        } else {
            context.getString(R.string.general_show_action)
        }
        toggleAction.text = toggleText
        
        toggleAction.setOnClickListener { item.onToggle() }
    }

    data class Item(
        val count: Int,
        val isExpanded: Boolean,
        val onToggle: () -> Unit,
    ) : OverviewAdapter.Item {
        override val stableId: Long = Item::class.hashCode().toLong()

        override val payloadProvider: ((DifferItem, DifferItem) -> DifferItem?)
            get() = { old, new -> if (new::class.isInstance(old)) new else null }
    }
}