package eu.darken.capod.devices.ui

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
import javax.inject.Inject

class DeviceManagerAdapter @Inject constructor() :
    ModularAdapter<DeviceManagerAdapter.BaseVH<DeviceManagerAdapter.Item, ViewBinding>>(),
    HasAsyncDiffer<DeviceManagerAdapter.Item> {

    override val asyncDiffer: AsyncDiffer<*, Item> = setupDiffer()

    init {
        modules.add(DataBinderMod(data))
        modules.add(TypedVHCreatorMod({ data[it] is DeviceProfileVH.Item }) { DeviceProfileVH(it) })
    }

    override fun getItemCount(): Int = data.size

    abstract class BaseVH<D : Item, B : ViewBinding>(
        @LayoutRes layoutId: Int,
        parent: ViewGroup
    ) : ModularAdapter.VH(layoutId, parent), BindableVH<D, B>

    interface Item : DifferItem
}