package eu.darken.capod.monitor.core

import eu.darken.capod.common.bluetooth.BleScanner
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.PodFactory
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
            scanResults
                .sortedByDescending { it.rssi }
                .mapNotNull { podFactory.createPod(it) }
        }

    companion object {
        private val TAG = logTag("Monitor", "PodMonitor")
    }
}