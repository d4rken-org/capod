package eu.darken.capod.main.ui.overview.cards

import android.view.ViewGroup
import eu.darken.capod.R
import eu.darken.capod.common.lists.binding
import eu.darken.capod.common.lists.differ.DifferItem
import eu.darken.capod.databinding.OverviewMonitoringActiveItemBinding
import eu.darken.capod.main.ui.overview.OverviewAdapter

class MonitoringActiveVH(parent: ViewGroup) :
    OverviewAdapter.BaseVH<MonitoringActiveVH.Item, OverviewMonitoringActiveItemBinding>(
        R.layout.overview_monitoring_active_item,
        parent
    ) {

    override val viewBinding = lazy {
        OverviewMonitoringActiveItemBinding.bind(itemView)
    }

    override val onBindData: OverviewMonitoringActiveItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding(payload = true) { item ->
        // No actions needed for this card - it's purely informational
    }

    object Item : OverviewAdapter.Item {
        override val stableId: Long = Item::class.hashCode().toLong()

        override val payloadProvider: ((DifferItem, DifferItem) -> DifferItem?)
            get() = { old, new -> if (new::class.isInstance(old)) new else null }
    }
}