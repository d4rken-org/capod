package eu.darken.capod.main.ui

import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.viewbinding.ViewBinding
import eu.darken.capod.common.lists.BindableVH
import eu.darken.capod.common.lists.differ.AsyncDiffer
import eu.darken.capod.common.lists.differ.DifferItem
import eu.darken.capod.common.lists.differ.HasAsyncDiffer
import eu.darken.capod.common.lists.differ.setupDiffer
import eu.darken.capod.common.lists.modular.ModularAdapter
import eu.darken.capod.common.lists.modular.mods.DataBinderMod
import eu.darken.capod.common.lists.modular.mods.TypedVHCreatorMod
import eu.darken.capod.main.ui.cards.PermissionCardVH
import eu.darken.capod.main.ui.cards.ToggleCardVH
import javax.inject.Inject

class MainAdapter @Inject constructor() :
    ModularAdapter<MainAdapter.BaseVH<MainAdapter.Item, ViewBinding>>(),
    HasAsyncDiffer<MainAdapter.Item> {

    override val asyncDiffer: AsyncDiffer<*, Item> = setupDiffer()

    init {
        modules.add(DataBinderMod(data))
        modules.add(TypedVHCreatorMod({ data[it] is ToggleCardVH.Item }) { ToggleCardVH(it) })
        modules.add(TypedVHCreatorMod({ data[it] is PermissionCardVH.Item }) { PermissionCardVH(it) })
    }

    override fun getItemCount(): Int = data.size

    abstract class BaseVH<D : Item, B : ViewBinding>(
        @LayoutRes layoutId: Int,
        parent: ViewGroup
    ) : ModularAdapter.VH(layoutId, parent), BindableVH<D, B>

    interface Item : DifferItem

}