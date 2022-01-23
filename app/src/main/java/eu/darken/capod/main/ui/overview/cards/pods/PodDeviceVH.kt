package eu.darken.capod.main.ui.overview.cards.pods

import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.LayoutRes
import androidx.viewbinding.ViewBinding
import eu.darken.capod.R
import eu.darken.capod.common.lists.BindableVH
import eu.darken.capod.common.lists.differ.DifferItem
import eu.darken.capod.common.lists.modular.ModularAdapter
import eu.darken.capod.main.ui.overview.OverviewAdapter
import eu.darken.capod.pods.core.PodDevice
import java.time.Instant

abstract class PodDeviceVH<D : PodDeviceVH.Item, B : ViewBinding>(
    @LayoutRes layoutId: Int,
    parent: ViewGroup
) : ModularAdapter.VH(layoutId, parent), BindableVH<D, B> {

    @DrawableRes
    fun getBatteryDrawable(percent: Float?): Int = when {
        percent == null -> R.drawable.ic_baseline_battery_unknown_24
        percent > 0.95f -> R.drawable.ic_baseline_battery_full_24
        percent > 0.80f -> R.drawable.ic_baseline_battery_6_bar_24
        percent > 0.65f -> R.drawable.ic_baseline_battery_5_bar_24
        percent > 0.50f -> R.drawable.ic_baseline_battery_4_bar_24
        percent > 0.35f -> R.drawable.ic_baseline_battery_3_bar_24
        percent > 0.20f -> R.drawable.ic_baseline_battery_2_bar_24
        percent > 0.05f -> R.drawable.ic_baseline_battery_1_bar_24
        else -> R.drawable.ic_baseline_battery_0_bar_24
    }

    interface Item : OverviewAdapter.Item {

        val now: Instant

        val device: PodDevice

        val showDebug: Boolean

        val isMainPod: Boolean

        override val stableId: Long get() = device.identifier.hashCode().toLong()

        override val payloadProvider: ((DifferItem, DifferItem) -> DifferItem?)?
            get() = { old, new ->
                if (new::class.isInstance(old)) new else null
            }

    }
}