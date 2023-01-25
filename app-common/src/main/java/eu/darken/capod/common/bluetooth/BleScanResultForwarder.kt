package eu.darken.capod.common.bluetooth

import android.bluetooth.le.ScanResult
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleScanResultForwarder @Inject constructor() {

    private val forwarder = MutableSharedFlow<Collection<ScanResult>>(
        replay = 0,
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val results: Flow<Collection<ScanResult>> = forwarder

    fun forward(scanResults: Collection<ScanResult>) {
        log(TAG, VERBOSE) { "forward($scanResults)" }
        val success = forwarder.tryEmit(scanResults)
        if (!success) log(TAG, WARN) { "Failed to forward (overflow?) $scanResults" }
    }

    companion object {
        private val TAG = logTag("Bluetooth", "BleScanner", "Forwarder")
    }
}