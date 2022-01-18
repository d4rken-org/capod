package eu.darken.capod.pods.core.apple.airpods

import android.content.Context
import eu.darken.capod.R
import eu.darken.capod.common.bluetooth.BleScanResult
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.DualApplePods
import eu.darken.capod.pods.core.apple.protocol.ProximityPairing
import java.time.Instant

data class AirPodsGen3 constructor(
    override val identifier: PodDevice.Id = PodDevice.Id(),
    override val lastSeenAt: Instant = Instant.now(),
    override val scanResult: BleScanResult,
    override val proximityMessage: ProximityPairing.Message,
    private val cachedBatteryPercentage: Float?,
) : DualApplePods {

    override fun getLabel(context: Context): String = "AirPods (Gen 3)"

    override val iconRes: Int
        get() = R.drawable.ic_device_airpods_gen2

    override val batteryCasePercent: Float?
        get() = super.batteryCasePercent ?: cachedBatteryPercentage

    companion object {
        val DEVICE_CODE = 0x1320.toUShort()
    }
}