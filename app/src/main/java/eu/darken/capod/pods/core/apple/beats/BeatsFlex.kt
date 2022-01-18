package eu.darken.capod.pods.core.apple.beats

import android.content.Context
import eu.darken.capod.R
import eu.darken.capod.common.bluetooth.BleScanResult
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.BasicSingleApplePods
import eu.darken.capod.pods.core.apple.protocol.ProximityPairing
import java.time.Instant

data class BeatsFlex(
    override val identifier: PodDevice.Id = PodDevice.Id(),
    override val lastSeenAt: Instant = Instant.now(),
    override val scanResult: BleScanResult,
    override val proximityMessage: ProximityPairing.Message
) : BasicSingleApplePods {

    override fun getLabel(context: Context): String = "Beats Flex"

    override val iconRes: Int
        get() = R.drawable.ic_device_generic_earbuds

    companion object {
        val DEVICE_CODE = 0x1020.toUShort()
    }
}