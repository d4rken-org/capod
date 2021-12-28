package eu.darken.cap.monitor.core

import eu.darken.cap.common.bluetooth.BleScanner
import eu.darken.cap.common.debug.logging.logTag
import eu.darken.cap.pods.core.PodDevice
import eu.darken.cap.pods.core.PodFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PodMonitor @Inject constructor(
    private val bleScanner: BleScanner,
    private val podFactory: PodFactory,
) {

    val pods: Flow<List<PodDevice>> = bleScanner.scan()
        .onStart { emptyList<PodDevice>() }
        .map { scanResults ->
            scanResults.mapNotNull { scanResult ->
                podFactory.createPod(scanResult)
            }
        }

    companion object {
        private val TAG = logTag("Monitor", "PodMonitor")
    }
}