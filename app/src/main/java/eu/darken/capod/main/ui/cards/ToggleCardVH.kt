package eu.darken.capod.main.ui.cards

import android.view.ViewGroup
import eu.darken.capod.R
import eu.darken.capod.databinding.MainToggleItemBinding
import eu.darken.capod.main.ui.MainAdapter

class ToggleCardVH(parent: ViewGroup) :
    MainAdapter.BaseVH<ToggleCardVH.Item, MainToggleItemBinding>(
        R.layout.main_toggle_item,
        parent
    ) {

    override val viewBinding = lazy {
        MainToggleItemBinding.bind(itemView)
    }

    override val onBindData: MainToggleItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = { item, _ ->
        toggleAction.text = when (item.isEnabled) {
            true -> "Disable"
            false -> "Enable"
        }
        itemView.setOnClickListener { item.onToggle() }
    }

    data class Item(
        val isEnabled: Boolean,
        val onToggle: () -> Unit
    ) : MainAdapter.Item {
        override val stableId: Long = this.javaClass.hashCode().toLong()
    }
}