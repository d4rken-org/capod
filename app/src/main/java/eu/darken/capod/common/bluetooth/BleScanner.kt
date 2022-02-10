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
        compatMode: Boolean,
    ): Flow<List<BleScanResult>> = callbackFlow {
        if (compatMode) log(TAG, WARN) { "Using compatibilityMode!" }

        val adapter = bluetoothManager.adapter

        val supportsOffloadFiltering = adapter.isOffloadedFilteringSupported.also {
            log(TAG, if (it) DEBUG else WARN) { "isOffloadedFilteringSupported=$it" }
        } && !compatMode

        val supportsOffloadBatching = adapter.isOffloadedScanBatchingSupported.also {
            log(TAG, if (it) DEBUG else WARN) { "isOffloadedScanBatchingSupported=$it" }
        } && !compatMode

        val scanner = bluetoothManager.scanner

        val callback = object : ScanCallback() {
            var lastScanAt = System.currentTimeMillis()
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                log(TAG, VERBOSE) {
                    val delay = System.currentTimeMillis() - lastScanAt
                    lastScanAt = System.currentTimeMillis()
                    "onScanResult(delay=${delay}ms, callbackType=$callbackType, result=$result)"
                }
                val toSend = if (supportsOffloadFiltering || filters.isEmpty() || filters.any { it.matches(result) }) {
                    listOf(BleScanResult.fromScanResult(result))
                } else {
                    log(TAG, VERBOSE) { "Manual filtering: No match for $result" }
                    emptyList()
                }
                trySend(toSend)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                log(TAG, VERBOSE) {
                    val delay = System.currentTimeMillis() - lastScanAt
                    lastScanAt = System.currentTimeMillis()
                    "onBatchScanResults(delay=${delay}ms, results=$results)"
                }

                val toSend = results
                    .filter { result ->
                        val passed = when {
                            supportsOffloadFiltering -> true
                            filters.isEmpty() -> true
                            else -> filters.any { it.matches(result) }
                        }
                        if (!passed) log(TAG, VERBOSE) { "Manually filtered $result" }
                        passed
                    }
                    .map { BleScanResult.fromScanResult(it) }
                trySend(toSend)
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
            if (supportsOffloadBatching) {
                setReportDelay(
                    when (scannerMode) {
                        ScannerMode.LOW_POWER -> 2000L
                        ScannerMode.BALANCED -> 1000L
                        ScannerMode.LOW_LATENCY -> 500L
                    }
                )
            }
        }.build()

        log(TAG, VERBOSE) { "Settings created for offloaded filtering: $settings" }

        val flushJob = launch {
            log(TAG) { "Flush job launched" }
            while (isActive) {
                // Can undercut the minimum setReportDelay(), e.g. 5000ms on a Pixel5@12
                log(TAG, VERBOSE) { "Flushing scan results." }
                adapter.bluetoothLeScanner.flushPendingScanResults(callback)
                when (scannerMode) {
                    ScannerMode.LOW_POWER -> break
                    ScannerMode.BALANCED -> delay(1000)
                    ScannerMode.LOW_LATENCY -> delay(500)
                }
            }
        }

        scanner.startScan(
            if (supportsOffloadFiltering) filters.toList() else listOf(ScanFilter.Builder().build()),
            settings,
            callback
        )
        log(TAG) { "BleScanner started (filters=$filters, settings=$settings)" }

        awaitClose {
            flushJob.cancel()
            scanner.stopScan(callback)
            log(TAG) { "BleScanner stopped" }
        }
    }
        .map { fakeBleData.maybeAddfakeData(it) }

    companion object {
        private val TAG = logTag("Bluetooth", "BleScanner")
    }
}