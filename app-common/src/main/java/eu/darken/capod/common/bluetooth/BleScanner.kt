package eu.darken.capod.common.bluetooth

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.debug.logging.Logging.Priority.*
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.notifications.PendingIntentCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bluetoothManager: BluetoothManager2,
    private val fakeBleData: FakeBleData,
    private val scanResultForwarder: BleScanResultForwarder,
) {

    @SuppressLint("MissingPermission") fun scan(
        filters: Set<ScanFilter>,
        scannerMode: ScannerMode = ScannerMode.BALANCED,
        disableOffloadFiltering: Boolean = false,
        disableOffloadBatching: Boolean = false,
        disableDirectScanCallback: Boolean = false,
    ): Flow<Collection<BleScanResult>> = callbackFlow {
        log(TAG) { "scan(filters=$filters, scannerMode=$scannerMode)" }

        val adapter = bluetoothManager.adapter ?: throw IllegalStateException("Bluetooth adapter unavailable")

        val useOffloadedFiltering = adapter.isOffloadedFilteringSupported.also {
            log(TAG, if (it) DEBUG else WARN) { "isOffloadedFilteringSupported=$it" }
        } && !disableOffloadFiltering
        if (disableOffloadFiltering) log(TAG, WARN) { "Offloaded filtering is disabled!" }

        val useOffloadedBatching = adapter.isOffloadedScanBatchingSupported.also {
            log(TAG, if (it) DEBUG else WARN) { "isOffloadedScanBatchingSupported=$it" }
        } && !disableOffloadBatching
        if (disableOffloadBatching) log(TAG, WARN) { "Offloaded scan-batching is disabled!" }

        if (disableDirectScanCallback) log(TAG, WARN) { "Direct scan callback is disabled!" }

        val scanner = bluetoothManager.scanner ?: throw IllegalStateException("BLE scanner unavailable")

        val filterResults: (Collection<ScanResult>) -> Collection<BleScanResult> = { results ->
            results
                .filter { result ->
                    val passed = when {
                        useOffloadedFiltering -> true
                        filters.isEmpty() -> true
                        else -> filters.any { it.matches(result) }
                    }
                    if (!passed) log(TAG, VERBOSE) { "Manually filtered $result" }
                    passed
                }
                .map { BleScanResult.fromScanResult(it) }
        }

        val callback = object : ScanCallback() {
            var lastScanAt = System.currentTimeMillis()
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                log(TAG, VERBOSE) {
                    val delay = System.currentTimeMillis() - lastScanAt
                    lastScanAt = System.currentTimeMillis()
                    "onScanResult(delay=${delay}ms, callbackType=$callbackType, result=$result)"
                }

                trySend(filterResults(setOf(result)))
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                log(TAG, VERBOSE) {
                    val delay = System.currentTimeMillis() - lastScanAt
                    lastScanAt = System.currentTimeMillis()
                    "onBatchScanResults(delay=${delay}ms, results=$results)"
                }

                trySend(filterResults(results))
            }

            override fun onScanFailed(errorCode: Int) {
                log(TAG, WARN) { "onScanFailed(errorCode=$errorCode)" }
            }
        }

        val forwarderConsumer = if (disableDirectScanCallback) {
            scanResultForwarder.results
                .onEach { results -> trySend(filterResults(results)) }
                .launchIn(this)
        } else {
            null
        }

        val flushJob = if (!disableDirectScanCallback) {
            launch {
                log(TAG) { "Flush job launched" }
                while (isActive) {
                    log(TAG, VERBOSE) { "Flushing scan results." }
                    // Can undercut the minimum setReportDelay(), e.g. 5000ms on a Pixel5@12
                    adapter.bluetoothLeScanner.flushPendingScanResults(callback)
                    when (scannerMode) {
                        ScannerMode.LOW_POWER -> break
                        ScannerMode.BALANCED -> delay(2000)
                        ScannerMode.LOW_LATENCY -> delay(500)
                    }
                }
            }
        } else {
            null
        }

        val filterList = when {
            useOffloadedFiltering -> filters.toList()
            else -> emptyList()
        }

        val scanSettings = ScanSettings.Builder().apply {
            setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            when (scannerMode) {
                ScannerMode.LOW_POWER -> {
                    setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                    setMatchMode(ScanSettings.MATCH_MODE_STICKY)
                    setNumOfMatches(ScanSettings.MATCH_NUM_FEW_ADVERTISEMENT)
                }
                ScannerMode.BALANCED -> {
                    setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                    setMatchMode(ScanSettings.MATCH_MODE_STICKY)
                    setNumOfMatches(ScanSettings.MATCH_NUM_FEW_ADVERTISEMENT)
                }
                ScannerMode.LOW_LATENCY -> {
                    setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                    setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
                }
            }

            val delay = if (useOffloadedBatching) {
                when (scannerMode) {
                    ScannerMode.LOW_POWER -> 2000L
                    ScannerMode.BALANCED -> 1000L
                    ScannerMode.LOW_LATENCY -> 500L
                }
            } else {
                0L // Anything > 0 enables batching
            }
            setReportDelay(delay)
        }.build()

        if (disableDirectScanCallback) {
            val callbackIntent = createStartIntent()
            log(TAG) { "Intent callback: startScan(filters=$filters, settings=$scanSettings, callbackIntent=$callbackIntent)" }
            scanner.startScan(filterList, scanSettings, callbackIntent)
        } else {
            log(TAG) { "Direct callback: startScan(filters=$filters, settings=$scanSettings, callback=$callback)" }
            scanner.startScan(filterList, scanSettings, callback)
        }

        awaitClose {
            forwarderConsumer?.cancel()
            flushJob?.cancel()
            if (disableDirectScanCallback) {
                scanner.stopScan(createStopIntent())
            } else {
                scanner.stopScan(callback)
            }
            log(TAG) { "BleScanner stopped" }
        }
    }
        .map { fakeBleData.maybeAddfakeData(it) }

    private val receiverIntent by lazy {
        Intent(context, BleScanResultReceiver::class.java).apply {
            action = BleScanResultReceiver.ACTION
        }
    }

    private fun createStartIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        CALLBACK_INTENT_REQUESTCODE,
        receiverIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntentCompat.FLAG_MUTABLE
    )

    private fun createStopIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        270,
        receiverIntent,
        PendingIntentCompat.FLAG_IMMUTABLE
    )

    companion object {
        private const val CALLBACK_INTENT_REQUESTCODE = 270
        private val TAG = logTag("Bluetooth", "BleScanner")
    }
}