package eu.darken.capod.pods.core.apple.airpods

import android.content.Context
import eu.darken.capod.R
import eu.darken.capod.common.bluetooth.BleScanResult
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.SingleApplePods
import eu.darken.capod.pods.core.apple.protocol.ProximityPairing
import java.time.Instant

data class AirPodsMax(
    override val identifier: PodDevice.Id = PodDevice.Id(),
    override val lastSeenAt: Instant = Instant.now(),
    override val scanResult: BleScanResult,
    override val proximityMessage: ProximityPairing.Message,
) : SingleApplePods {

    override fun getLabel(context: Context): String = "AirPods Max"

    override val iconRes: Int
        get() = R.drawable.ic_device_generic_headphones

    companion object {
        val DEVICE_CODE_DIRTY = 10.toUByte()
    }
}