package eu.darken.capod.main.ui.overview

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
import eu.darken.capod.main.ui.overview.cards.BluetoothDisabledVH
import eu.darken.capod.main.ui.overview.cards.MissingMainDeviceVH
import eu.darken.capod.main.ui.overview.cards.NoPairedDeviceCardVH
import eu.darken.capod.main.ui.overview.cards.PermissionCardVH
import eu.darken.capod.main.ui.overview.cards.pods.BasicSingleApplePodsCardVH
import eu.darken.capod.main.ui.overview.cards.pods.DualApplePodsCardVH
import eu.darken.capod.main.ui.overview.cards.pods.SingleApplePodsCardVH
import eu.darken.capod.main.ui.overview.cards.pods.UnknownPodDeviceCardVH
import javax.inject.Inject

class OverviewAdapter @Inject constructor() :
    ModularAdapter<OverviewAdapter.BaseVH<OverviewAdapter.Item, ViewBinding>>(),
    HasAsyncDiffer<OverviewAdapter.Item> {

    override val asyncDiffer: AsyncDiffer<*, Item> = setupDiffer()

    init {
        modules.add(DataBinderMod(data))
        modules.add(TypedVHCreatorMod({ data[it] is PermissionCardVH.Item }) { PermissionCardVH(it) })
        modules.add(TypedVHCreatorMod({ data[it] is DualApplePodsCardVH.Item }) { DualApplePodsCardVH(it) })
        modules.add(TypedVHCreatorMod({ data[it] is SingleApplePodsCardVH.Item }) { SingleApplePodsCardVH(it) })
        modules.add(TypedVHCreatorMod({ data[it] is BasicSingleApplePodsCardVH.Item }) { BasicSingleApplePodsCardVH(it) })
        modules.add(TypedVHCreatorMod({ data[it] is UnknownPodDeviceCardVH.Item }) { UnknownPodDeviceCardVH(it) })
        modules.add(TypedVHCreatorMod({ data[it] is NoPairedDeviceCardVH.Item }) { NoPairedDeviceCardVH(it) })
        modules.add(TypedVHCreatorMod({ data[it] is MissingMainDeviceVH.Item }) { MissingMainDeviceVH(it) })
        modules.add(TypedVHCreatorMod({ data[it] is BluetoothDisabledVH.Item }) { BluetoothDisabledVH(it) })
    }

    override fun getItemCount(): Int = data.size

    abstract class BaseVH<D : Item, B : ViewBinding>(
        @LayoutRes layoutId: Int,
        parent: ViewGroup
    ) : ModularAdapter.VH(layoutId, parent), BindableVH<D, B>

    interface Item : DifferItem

}