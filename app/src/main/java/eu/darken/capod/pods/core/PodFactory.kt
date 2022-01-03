package eu.darken.capod.pods.core

import android.bluetooth.le.ScanResult
import dagger.Reusable
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.pods.core.apple.AppleFactory
import javax.inject.Inject

@Reusable
class PodFactory @Inject constructor(
    private val appleFactory: AppleFactory
) {

    suspend fun createPod(scanResult: ScanResult): PodDevice? {
        log(TAG, VERBOSE) { "Trying to create Pod for $scanResult" }

        val pod = appleFactory.create(scanResult)
        if (pod != null) {
            log(TAG) { "Pod created: $pod" }
            return pod
        } else {
            log(TAG, WARN) { "Not an AirPod : $scanResult" }
        }


        log(TAG, WARN) { "Failed to find matching PodFactory for $scanResult" }
        return null
    }

    companion object {
        private val TAG = logTag("Pod", "Factory")
    }
}