package eu.darken.capod.main.ui.overview.cards.pods

import android.icu.text.RelativeDateTimeFormatter
import android.view.ViewGroup
import eu.darken.capod.R
import eu.darken.capod.common.lists.binding
import eu.darken.capod.databinding.OverviewPodsUnknownItemBinding
import eu.darken.capod.pods.core.PodDevice
import java.time.Duration
import java.time.Instant

class UnknownPodDeviceCardVH(parent: ViewGroup) :
    PodDeviceVH<UnknownPodDeviceCardVH.Item, OverviewPodsUnknownItemBinding>(
        R.layout.overview_pods_unknown_item,
        parent
    ) {

    override val viewBinding = lazy {
        OverviewPodsUnknownItemBinding.bind(itemView)
    }

    private val lastSeenFormatter = RelativeDateTimeFormatter.getInstance()

    override val onBindData = binding(payload = true) { item ->
        val device = item.device
        name.text = device.getLabel(context)

        val duration = Duration.between(device.lastSeenAt, item.now)
        lastSeen.text = lastSeenFormatter.format(
            duration.seconds.toDouble(),
            RelativeDateTimeFormatter.Direction.LAST,
            RelativeDateTimeFormatter.RelativeUnit.SECONDS
        )

        rawdata.text = device.rawDataHex
    }

    data class Item(
        override val now: Instant,
        override val device: PodDevice,
        override val showDebug: Boolean = false
    ) : PodDeviceVH.Item
}