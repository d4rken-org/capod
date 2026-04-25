package eu.darken.capod.pods.core.apple.ble

import dagger.Reusable
import eu.darken.capod.common.bluetooth.BleScanResult
import eu.darken.capod.common.bluetooth.logSummary
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.pods.core.unknown.UnknownDeviceFactory
import javax.inject.Inject

@Reusable
class PodFactory @Inject constructor(
    private val appleFactory: AppleFactory,
    private val unknownFactory: UnknownDeviceFactory,
) {

    suspend fun createPod(scanResult: BleScanResult): Result? {
        log(TAG, VERBOSE) { "Decoding ${scanResult.logSummary()}" }

        var device = appleFactory.create(scanResult)

        if (device == null) {
            log(TAG, VERBOSE) { "Using fallback factory" }
            device = unknownFactory.create(scanResult)
        }

        log(TAG, VERBOSE) { "Pod created: ${device.logSummary()}" }
        return Result(scanResult = scanResult, device = device)
    }

    data class Result(
        val scanResult: BleScanResult,
        val device: BlePodSnapshot
    )

    companion object {
        private val TAG = logTag("Pod", "Factory")
    }
}
