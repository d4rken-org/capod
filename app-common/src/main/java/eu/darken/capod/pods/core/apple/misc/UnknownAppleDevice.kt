package eu.darken.capod.pods.core.apple.misc

import android.content.Context
import eu.darken.capod.R
import eu.darken.capod.common.bluetooth.BleScanResult
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.ApplePods
import eu.darken.capod.pods.core.apple.ApplePodsFactory
import eu.darken.capod.pods.core.apple.protocol.ProximityPairing
import java.time.Instant
import javax.inject.Inject

data class UnknownAppleDevice(
    override val identifier: PodDevice.Id = PodDevice.Id(),
    override val seenLastAt: Instant = Instant.now(),
    override val seenFirstAt: Instant = Instant.now(),
    override val seenCounter: Int = 1,
    override val scanResult: BleScanResult,
    override val proximityMessage: ProximityPairing.Message,
    override val confidence: Float = 0f,
    private val rssiAverage: Int? = null,
) : ApplePods {

    override val model: PodDevice.Model = PodDevice.Model.UNKNOWN

    override fun getLabel(context: Context): String = context.getString(R.string.pods_unknown_label)

    override val rssi: Int
        get() = rssiAverage ?: super.rssi

    class Factory @Inject constructor() : ApplePodsFactory<ApplePods>(TAG) {
        override fun isResponsible(message: ProximityPairing.Message): Boolean = true

        override fun create(
            scanResult: BleScanResult,
            message: ProximityPairing.Message,
        ): ApplePods {
            var basic = UnknownAppleDevice(scanResult = scanResult, proximityMessage = message)
            val result = searchHistory(basic)

            if (result != null) basic = basic.copy(identifier = result.id)
            updateHistory(basic)

            if (result == null) return basic

            return basic.copy(
                identifier = result.id,
                seenFirstAt = result.seenFirstAt,
                seenLastAt = scanResult.receivedAt,
                seenCounter = result.seenCounter,
                confidence = result.confidence,
                rssiAverage = result.averageRssi(basic.rssi),
            )
        }
    }

    companion object {
        private val TAG = logTag("PodDevice", "Apple", "Unknown")
    }
}