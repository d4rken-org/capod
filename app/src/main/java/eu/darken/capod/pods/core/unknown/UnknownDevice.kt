package eu.darken.capod.pods.core.unknown

import android.content.Context
import eu.darken.capod.common.R
import eu.darken.capod.common.bluetooth.BleScanResult
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.pods.core.PodDevice
import java.time.Instant

data class UnknownDevice(
    override val identifier: PodDevice.Id = PodDevice.Id(),
    override val seenLastAt: Instant = Instant.now(),
    override val seenFirstAt: Instant = Instant.now(),
    override val seenCounter: Int = 1,
    override val scanResult: BleScanResult,
    override val reliability: Float = 0f,
    private val rssiAverage: Int? = null,
) : PodDevice {

    override val model: PodDevice.Model = PodDevice.Model.UNKNOWN

    override fun getLabel(context: Context): String = context.getString(R.string.pods_unknown_label)

    override val rssi: Int
        get() = rssiAverage ?: super.rssi

    companion object {
        private val TAG = logTag("PodDevice", "Unknown")
    }
}