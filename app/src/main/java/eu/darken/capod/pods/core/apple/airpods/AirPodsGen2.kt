package eu.darken.capod.pods.core.apple.airpods

import android.bluetooth.le.ScanResult
import android.content.Context
import eu.darken.capod.R
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.pods.core.apple.DualApplePods
import eu.darken.capod.pods.core.apple.protocol.ProximityPairing
import java.time.Instant
import java.util.*

data class AirPodsGen2 constructor(
    override val identifier: UUID = UUID.randomUUID(),
    override val lastSeenAt: Instant = Instant.now(),
    override val scanResult: ScanResult,
    override val proximityMessage: ProximityPairing.Message,
    private val cachedBatteryPercentage: Float?,
) : DualApplePods {

    override fun getLabel(context: Context): String = "AirPods (Gen 2)"

    override val iconRes: Int
        get() = R.drawable.ic_device_airpods_gen2

    override val tag: String = logTag("Pod", "Apple", "AirPods", "Gen2")

    override val batteryCasePercent: Float?
        get() = super.batteryCasePercent ?: cachedBatteryPercentage
}