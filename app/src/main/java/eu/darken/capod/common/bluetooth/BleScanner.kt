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
import eu.darken.capod.common.TimeSource
import eu.darken.capod.common.debug.Bugs
import eu.darken.capod.common.debug.logging.Logging.Priority.DEBUG
import eu.darken.capod.common.debug.logging.Logging.Priority.INFO
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.notifications.PendingIntentCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bluetoothManager: BluetoothManager2,
    private val scanResultForwarder: BleScanResultForwarder,
    private val timeSource: TimeSource,
) {

    @SuppressLint("MissingPermission") fun scan(
        filters: Set<ScanFilter>,
        filterPolicy: ScanFilterPolicy,
        scannerMode: ScannerMode = ScannerMode.BALANCED,
        disableOffloadFiltering: Boolean = false,
        disableOffloadBatching: Boolean = false,
        disableDirectScanCallback: Boolean = false,
    ): Flow<Collection<BleScanResult>> = callbackFlow {
        log(TAG) {
            "scan(filterCount=${filters.size}, scannerMode=$scannerMode, directCallback=${!disableDirectScanCallback})"
        }

        val adapter = bluetoothManager.adapter ?: throw IllegalStateException("Bluetooth adapter unavailable")

        val offloadFilteringSupported = adapter.isOffloadedFilteringSupported.also {
            log(TAG, if (it) DEBUG else WARN) { "isOffloadedFilteringSupported=$it" }
        }
        val useOffloadedFiltering = offloadFilteringSupported && !disableOffloadFiltering
        if (disableOffloadFiltering) log(TAG, WARN) { "Offloaded filtering is disabled!" }

        val offloadBatchingSupported = adapter.isOffloadedScanBatchingSupported.also {
            log(TAG, if (it) DEBUG else WARN) { "isOffloadedScanBatchingSupported=$it" }
        }
        val useOffloadedBatching = offloadBatchingSupported && !disableOffloadBatching
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
                    if (!passed) log(TAG, VERBOSE) { "Manually filtered ${result.logSummary()}" }
                    passed
                }
                .map { BleScanResult.fromScanResult(it, timeSource) }
        }

        val callback = object : ScanCallback() {
            // Updated outside the log lambdas below: those only run while a logger is attached, so
            // folding the bookkeeping into them made the first delay of a debug recording measure
            // the time since the *previous* recording ended instead of the actual callback gap.
            // Monotonic clock, so a wall-clock correction can't fabricate a gap either.
            var lastScanAt = timeSource.elapsedRealtime()

            private fun takeDelay(): Long {
                val now = timeSource.elapsedRealtime()
                val delay = now - lastScanAt
                lastScanAt = now
                return delay
            }

            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val delay = takeDelay()
                log(TAG, VERBOSE) {
                    "onScanResult(delay=${delay}ms, callbackType=$callbackType, ${result.logSummary()})"
                }

                trySend(filterResults(setOf(result)))
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                val delay = takeDelay()
                log(TAG, VERBOSE) { "onBatchScanResults(delay=${delay}ms, ${results.logSummary()})" }

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

        var flushJob: Job? = null

        val filterList = when {
            useOffloadedFiltering -> filters.toList()
            else -> emptyList()
        }

        val reportDelayMs = if (useOffloadedBatching) {
            when (scannerMode) {
                ScannerMode.LOW_POWER -> 2000L
                ScannerMode.BALANCED -> 1000L
                ScannerMode.LOW_LATENCY -> 500L
            }
        } else {
            0L // Anything > 0 enables batching
        }

        val scanSettings = ScanSettings.Builder().apply {
            setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            when (scannerMode) {
                ScannerMode.LOW_POWER -> {
                    setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                    setMatchMode(ScanSettings.MATCH_MODE_STICKY)
                    setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
                }
                ScannerMode.BALANCED -> {
                    setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                    setMatchMode(ScanSettings.MATCH_MODE_STICKY)
                    setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
                }
                ScannerMode.LOW_LATENCY -> {
                    setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                    setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
                }
            }

            setReportDelay(reportDelayMs)
        }.build()

        val config = ScanConfig(
            scannerMode = scannerMode,
            filterPolicy = filterPolicy,
            platformFilterCount = filterList.size,
            requestedFilterCount = filters.size,
            offloadFilteringSupported = offloadFilteringSupported,
            offloadFilteringDisabledBySetting = disableOffloadFiltering,
            offloadBatchingSupported = offloadBatchingSupported,
            offloadBatchingDisabledBySetting = disableOffloadBatching,
            reportDelayMs = reportDelayMs,
            directCallback = !disableDirectScanCallback,
        )
        log(TAG, INFO) { config.summary() }

        // A recording usually starts while a scan is already running, i.e. after the line above went
        // nowhere. Re-emit so the capture contains the configuration it is meant to diagnose.
        val debugAtStart = Bugs.isDebug.value
        Bugs.isDebug
            // Not drop(1): launchIn subscribes asynchronously, so the replayed value is whatever is
            // current at subscription time. A recording started in that gap would be discarded as
            // though it were the initial value.
            .dropWhile { it == debugAtStart }
            .filter { it }
            .onEach { log(TAG, INFO) { "${config.summary()} (recording started)" } }
            .launchIn(this)

        try {
            if (disableDirectScanCallback) {
                val callbackIntent = createStartIntent()
                log(TAG) {
                    "startScan(mode=$scannerMode, filterCount=${filterList.size}, batching=$useOffloadedBatching, filtering=$useOffloadedFiltering, callback=intent)"
                }
                scanner.startScan(filterList, scanSettings, callbackIntent)
            } else {
                log(TAG) {
                    "startScan(mode=$scannerMode, filterCount=${filterList.size}, batching=$useOffloadedBatching, filtering=$useOffloadedFiltering, callback=direct)"
                }
                scanner.startScan(filterList, scanSettings, callback)
                flushJob = launch {
                    log(TAG) { "Flush job launched" }
                    while (isActive) {
                        try {
                            // Can undercut the minimum setReportDelay(), e.g. 5000ms on a Pixel5@12
                            scanner.flushPendingScanResults(callback)
                        } catch (e: SecurityException) {
                            log(TAG, WARN) { "flushPendingScanResults() denied: ${e.message}" }
                            close(e)
                            break
                        }
                        when (scannerMode) {
                            ScannerMode.LOW_POWER -> break
                            ScannerMode.BALANCED -> delay(2000)
                            ScannerMode.LOW_LATENCY -> delay(500)
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            log(TAG, WARN) { "startScan() denied: ${e.message}" }
            forwarderConsumer?.cancel()
            close(e)
            return@callbackFlow
        }

        awaitClose {
            forwarderConsumer?.cancel()
            flushJob?.cancel()
            try {
                if (disableDirectScanCallback) {
                    scanner.stopScan(createStopIntent())
                } else {
                    scanner.stopScan(callback)
                }
            } catch (e: SecurityException) {
                log(TAG, WARN) { "stopScan() denied: ${e.message}" }
            }
            log(TAG) { "BleScanner stopped" }
        }
    }

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

internal data class ScanConfig(
    val scannerMode: ScannerMode,
    val filterPolicy: ScanFilterPolicy,
    val platformFilterCount: Int,
    val requestedFilterCount: Int,
    val offloadFilteringSupported: Boolean,
    val offloadFilteringDisabledBySetting: Boolean,
    val offloadBatchingSupported: Boolean,
    val offloadBatchingDisabledBySetting: Boolean,
    val reportDelayMs: Long,
    val directCallback: Boolean,
) {
    // "Requested" rather than "filtering"/"batching": adapter capability plus our own setting is
    // what we asked the platform for, not proof that the controller offloaded anything.
    fun summary(): String {
        val offloadFilteringRequested = offloadFilteringSupported && !offloadFilteringDisabledBySetting
        val offloadBatchingRequested = offloadBatchingSupported && !offloadBatchingDisabledBySetting
        return "Scan config: mode=$scannerMode, filterPolicy=$filterPolicy, " +
            "platformFilters=$platformFilterCount/$requestedFilterCount, " +
            "offloadFilteringRequested=$offloadFilteringRequested " +
            "(supported=$offloadFilteringSupported, disabledBySetting=$offloadFilteringDisabledBySetting), " +
            "offloadBatchingRequested=$offloadBatchingRequested " +
            "(supported=$offloadBatchingSupported, disabledBySetting=$offloadBatchingDisabledBySetting), " +
            "reportDelay=${reportDelayMs}ms, callback=${if (directCallback) "direct" else "intent"}"
    }
}
