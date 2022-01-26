package eu.darken.capod.pods.core.apple.beats

import eu.darken.capod.common.bluetooth.BleScanResult
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.ApplePods
import eu.darken.capod.pods.core.apple.DualApplePods
import eu.darken.capod.pods.core.apple.protocol.ProximityPairing
import java.time.Instant
import javax.inject.Inject

data class PowerBeatsPro(
    override val identifier: PodDevice.Id = PodDevice.Id(),
    override val lastSeenAt: Instant = Instant.now(),
    override val scanResult: BleScanResult,
    override val proximityMessage: ProximityPairing.Message,
    private val cachedBatteryPercentage: Float?,
    override val rssiHistory: List<Int>,
) : DualApplePods {

    override val model: PodDevice.Model = PodDevice.Model.POWERBEATS_PRO

    override val batteryCasePercent: Float?
        get() = super.batteryCasePercent ?: cachedBatteryPercentage

    class Factory @Inject constructor() : ApplePods.Factory(TAG) {

        override fun isResponsible(proximityMessage: ProximityPairing.Message): Boolean =
            proximityMessage.getModelInfo().dirty == DEVICE_CODE_DIRTY

        override fun create(scanResult: BleScanResult, proximityMessage: ProximityPairing.Message): ApplePods {
            val recognized = recognizeDevice(scanResult, proximityMessage)

            val device = PowerBeatsPro(
                identifier = recognized.identifier,
                scanResult = scanResult,
                proximityMessage = proximityMessage,
                cachedBatteryPercentage = cachedValues[recognized.identifier]?.caseBatteryPercentage,
                rssiHistory = recognized.rssiHistory,
            )

            cachedValues[recognized.identifier] = ValueCache(
                caseBatteryPercentage = device.batteryCasePercent
            )

            return device
        }

    }

    companion object {
        private val DEVICE_CODE_DIRTY = 11.toUByte()
        private val TAG = logTag("PodDevice", "Beats", "PowerBeats", "Pro")
    }
}