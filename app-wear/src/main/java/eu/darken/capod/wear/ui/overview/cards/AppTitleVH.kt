package eu.darken.capod.wear.ui.overview.cards

import android.view.ViewGroup
import eu.darken.capod.R
import eu.darken.capod.common.lists.binding
import eu.darken.capod.common.lists.differ.DifferItem
import eu.darken.capod.common.upgrade.UpgradeRepo
import eu.darken.capod.databinding.OverviewApptitleItemBinding
import eu.darken.capod.wear.ui.overview.OverviewAdapter

class AppTitleVH(parent: ViewGroup) :
    OverviewAdapter.BaseVH<AppTitleVH.Item, OverviewApptitleItemBinding>(
        R.layout.overview_apptitle_item,
        parent
    ) {

    override val viewBinding = lazy {
        OverviewApptitleItemBinding.bind(itemView)
    }

    override val onBindData: OverviewApptitleItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding(payload = true) { item ->

    }

    data class Item(
        val upgradeInfo: UpgradeRepo.Info? = null
    ) : OverviewAdapter.Item {
        override val stableId: Long = Item::class.hashCode().toLong()

        override val payloadProvider: ((DifferItem, DifferItem) -> DifferItem?)
            get() = { old, new -> if (new::class.isInstance(old)) new else null }
    }
}