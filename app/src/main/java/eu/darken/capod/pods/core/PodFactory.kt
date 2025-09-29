package eu.darken.capod.pods.core

import dagger.Reusable
import eu.darken.capod.common.bluetooth.BleScanResult
import eu.darken.capod.common.debug.logging.Logging.Priority.INFO
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.pods.core.apple.AppleFactory
import eu.darken.capod.pods.core.unknown.UnknownDeviceFactory
import javax.inject.Inject

@Reusable
class PodFactory @Inject constructor(
    private val appleFactory: AppleFactory,
    private val unknownFactory: UnknownDeviceFactory,
) {

    suspend fun createPod(scanResult: BleScanResult): Result? {
        log(TAG, VERBOSE) { "Trying to create Pod for $scanResult" }

        log(TAG, INFO) { "Decoding $scanResult" }

        var device = appleFactory.create(scanResult)

        if (device == null) {
            log(TAG, VERBOSE) { "Using fallback factory" }
            device = unknownFactory.create(scanResult)
        }

        log(TAG, INFO) { "Pod created: $device" }
        return device?.let {
            Result(scanResult = scanResult, device = it)
        }
    }

    data class Result(
        val scanResult: BleScanResult,
        val device: PodDevice
    )

    companion object {
        private val TAG = logTag("Pod", "Factory")
    }
}