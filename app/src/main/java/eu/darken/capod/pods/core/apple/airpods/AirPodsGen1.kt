package eu.darken.capod.pods.core.apple.airpods

import eu.darken.capod.common.bluetooth.BleScanResult
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.ApplePods
import eu.darken.capod.pods.core.apple.DualApplePods
import eu.darken.capod.pods.core.apple.protocol.ProximityPairing
import java.time.Instant
import javax.inject.Inject

data class AirPodsGen1 constructor(
    override val identifier: PodDevice.Id = PodDevice.Id(),
    override val lastSeenAt: Instant = Instant.now(),
    override val scanResult: BleScanResult,
    override val proximityMessage: ProximityPairing.Message,
    private val cachedBatteryPercentage: Float?
) : DualApplePods {

    override val model: PodDevice.Model = PodDevice.Model.AIRPODS_GEN1

    override val batteryCasePercent: Float?
        get() = super.batteryCasePercent ?: cachedBatteryPercentage

    class Factory @Inject constructor() : ApplePods.Factory(TAG) {

        override fun isResponsible(proximityMessage: ProximityPairing.Message): Boolean =
            proximityMessage.getModelInfo().full == DEVICE_CODE

        override fun create(scanResult: BleScanResult, proximityMessage: ProximityPairing.Message): ApplePods {
            val identifier = recognizeDevice(scanResult, proximityMessage)

            val device = AirPodsGen1(
                identifier = identifier,
                scanResult = scanResult,
                proximityMessage = proximityMessage,
                cachedBatteryPercentage = cachedValues[identifier]?.caseBatteryPercentage
            )

            cachedValues[identifier] = ValueCache(
                caseBatteryPercentage = device.batteryCasePercent
            )

            return device
        }
    }

    companion object {
        private val DEVICE_CODE = 0x0220.toUShort()
        private val TAG = logTag("PodDevice", "Apple", "AirPods", "Gen1")
    }
}