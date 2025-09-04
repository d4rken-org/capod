package eu.darken.capod.main.ui.overview.cards.pods

import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.viewbinding.ViewBinding
import eu.darken.capod.common.lists.BindableVH
import eu.darken.capod.common.lists.differ.DifferItem
import eu.darken.capod.common.lists.modular.ModularAdapter
import eu.darken.capod.main.ui.overview.OverviewAdapter
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.getSignalQuality
import java.time.Instant

abstract class PodDeviceVH<D : PodDeviceVH.Item, B : ViewBinding>(
    @LayoutRes layoutId: Int,
    parent: ViewGroup
) : ModularAdapter.VH(layoutId, parent), BindableVH<D, B> {

    fun Item.getReceptionText(): String = device.getSignalQuality(context)
        .let { if (showDebug) "$it ${device.seenCounter}" else it }

    interface Item : OverviewAdapter.Item {

        val now: Instant

        val device: PodDevice

        val showDebug: Boolean

        override val stableId: Long get() = device.identifier.hashCode().toLong()

        override val payloadProvider: ((DifferItem, DifferItem) -> DifferItem?)?
            get() = { old, new ->
                if (new::class.isInstance(old)) new else null
            }

    }
}