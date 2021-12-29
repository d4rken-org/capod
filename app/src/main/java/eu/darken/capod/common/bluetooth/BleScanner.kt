package eu.darken.capod.common.bluetooth

import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.debug.logging.Logging.Priority.*
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.pods.core.airpods.ProximityPairing
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bluetoothManager: BluetoothManager,
) {
    // TODO check Bluetooth available
    // TODO check Bluetooth enabled
    fun scan(
        filter: Set<ScanFilter> = ProximityPairing.getBleScanFilter(),
        mode: Int = ScanSettings.SCAN_MODE_LOW_LATENCY,
        delay: Long = 1,
    ): Flow<List<ScanResult>> = callbackFlow {
        val scanner = bluetoothManager.adapter.bluetoothLeScanner

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                log(TAG, VERBOSE) { "onScanResult(callbackType=$callbackType, result=$result)" }
                trySend(listOf(result))
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                log(TAG, VERBOSE) { "onBatchScanResults(results=$results)" }
                trySend(results)
            }

            override fun onScanFailed(errorCode: Int) {
                log(TAG, WARN) { "onScanFailed(errorCode=$errorCode)" }
            }
        }

        val settings = ScanSettings.Builder().apply {
            setScanMode(mode)
            setReportDelay(delay)
        }.build()

        scanner.startScan(filter.toList(), settings, callback)
        log(TAG, VERBOSE) { "BleScanner started (filter=$filter, settings=$settings)" }

        awaitClose {
            log(TAG, INFO) { "BleScanner stopped" }
            scanner.stopScan(callback)
        }
    }


    companion object {
        private val TAG = logTag("Bluetooth", "BleScanner")
    }
}