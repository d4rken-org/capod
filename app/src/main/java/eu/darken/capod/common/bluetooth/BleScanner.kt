package eu.darken.capod.common.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.debug.logging.Logging.Priority.*
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.main.core.ScannerMode
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bluetoothManager: BluetoothManager2,
    private val fakeBleData: FakeBleData,
) {

    @SuppressLint("MissingPermission") fun scan(
        filters: Set<ScanFilter>,
        scannerMode: ScannerMode,
    ): Flow<List<BleScanResult>> = callbackFlow {
        val adapter = bluetoothManager.adapter
        val scanner = bluetoothManager.scanner

        val callback = object : ScanCallback() {
            var lastScanAt = System.currentTimeMillis()
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                log(TAG, VERBOSE) {
                    val delay = System.currentTimeMillis() - lastScanAt
                    lastScanAt = System.currentTimeMillis()
                    "onScanResult(delay=${delay}ms, callbackType=$callbackType, result=$result)"
                }
                trySend(listOf(BleScanResult.fromScanResult(result)))
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                log(TAG, VERBOSE) {
                    val delay = System.currentTimeMillis() - lastScanAt
                    lastScanAt = System.currentTimeMillis()
                    "onBatchScanResults(delay=${delay}ms, results=$results)"
                }
                trySend(results.map { BleScanResult.fromScanResult(it) })
            }

            override fun onScanFailed(errorCode: Int) {
                log(TAG, WARN) { "onScanFailed(errorCode=$errorCode)" }
            }
        }

        val settings = ScanSettings.Builder().apply {
            setScanMode(
                when (scannerMode) {
                    ScannerMode.LOW_POWER -> ScanSettings.SCAN_MODE_LOW_POWER
                    ScannerMode.BALANCED -> ScanSettings.SCAN_MODE_BALANCED
                    ScannerMode.LOW_LATENCY -> ScanSettings.SCAN_MODE_LOW_LATENCY
                }
            )
            if (adapter.isOffloadedScanBatchingSupported) {
                when (scannerMode) {
                    ScannerMode.LOW_POWER -> setReportDelay(2000)
                    ScannerMode.BALANCED -> setReportDelay(1000)
                    ScannerMode.LOW_LATENCY -> setReportDelay(500)
                }
            } else {
                log(TAG, WARN) { "isOffloadedScanBatchingSupported=false" }
            }
        }.build()

        val flushJob = launch {
            while (isActive) {
                // Can undercut the minimum setReportDelay(), e.g. 5000ms on a Pixel5@12
                adapter.bluetoothLeScanner.flushPendingScanResults(callback)
                when (scannerMode) {
                    ScannerMode.LOW_POWER -> break
                    ScannerMode.BALANCED -> delay(1000)
                    ScannerMode.LOW_LATENCY -> delay(500)
                }
            }
        }

        if (adapter.isOffloadedFilteringSupported) {
            scanner.startScan(filters.toList(), settings, callback)
            log(TAG, VERBOSE) { "BleScanner started (filters=$filters, settings=$settings)" }
        } else {
            log(TAG, WARN) { "isOffloadedFilteringSupported=false" }
            scanner.startScan(callback)
            log(TAG, VERBOSE) { "BleScanner started" }
        }

        awaitClose {
            flushJob.cancel()
            scanner.stopScan(callback)
            log(TAG, INFO) { "BleScanner stopped" }
        }
    }
        .map { fakeBleData.maybeAddfakeData(it) }

    companion object {
        private val TAG = logTag("Bluetooth", "BleScanner")
    }
}