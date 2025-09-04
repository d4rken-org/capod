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
        modules.add(TypedVHCreatorMod({ data[it] is NoProfilesCardVH.Item }) { NoProfilesCardVH(it) })
    }

    override fun getItemCount(): Int = data.size

    fun moveItem(fromPosition: Int, toPosition: Int): Boolean {
        if (fromPosition < 0 || toPosition < 0 || fromPosition >= data.size || toPosition >= data.size) {
            return false
        }
        
        val currentData = data.toMutableList()
        val item = currentData.removeAt(fromPosition)
        currentData.add(toPosition, item)
        
        // Update the adapter data through the differ for proper visual feedback
        asyncDiffer.submitUpdate(currentData)
        notifyItemMoved(fromPosition, toPosition)
        return true
    }

    fun getItems(): List<Item> = data.toList()

    abstract class BaseVH<D : Item, B : ViewBinding>(
        @LayoutRes layoutId: Int,
        parent: ViewGroup
    ) : ModularAdapter.VH(layoutId, parent), BindableVH<D, B>

    interface Item : DifferItem
}