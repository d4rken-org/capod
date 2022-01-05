package eu.darken.capod.pods.core.apple.airpods

import android.bluetooth.le.ScanResult
import android.content.Context
import eu.darken.capod.R
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.DualApplePods
import eu.darken.capod.pods.core.apple.protocol.ProximityPairing
import java.time.Instant

data class AirPodsPro constructor(
    override val identifier: PodDevice.Id = PodDevice.Id(),
    override val lastSeenAt: Instant = Instant.now(),
    override val scanResult: ScanResult,
    override val proximityMessage: ProximityPairing.Message,
    private val cachedBatteryPercentage: Float?,
) : DualApplePods {

    override fun getLabel(context: Context): String {
        return "AirPods Pro"
    }

    override val iconRes: Int
        get() = R.drawable.ic_device_airpods_gen2

    override val batteryCasePercent: Float?
        get() = super.batteryCasePercent ?: cachedBatteryPercentage
}