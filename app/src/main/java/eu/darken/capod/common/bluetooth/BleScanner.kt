package eu.darken.capod.common.bluetooth

import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.SystemClockWrap
import eu.darken.capod.common.debug.autoreport.DebugSettings
import eu.darken.capod.common.debug.logging.Logging.Priority.*
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.pods.core.apple.protocol.ProximityPairing
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bluetoothManager: BluetoothManager2,
    private val debugSettings: DebugSettings,
) {

    fun scan(
        filter: Set<ScanFilter> = ProximityPairing.getBleScanFilter(),
        mode: Int = ScanSettings.SCAN_MODE_BALANCED,
    ): Flow<List<BleScanResult>> = callbackFlow {
        val adapter = bluetoothManager.adapter
        val scanner = bluetoothManager.scanner

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                log(TAG, VERBOSE) { "onScanResult(callbackType=$callbackType, result=$result)" }
                trySend(listOf(BleScanResult.fromScanResult(result)))
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                log(TAG, VERBOSE) { "onBatchScanResults(results=$results)" }
                trySend(results.map { BleScanResult.fromScanResult(it) })
            }

            override fun onScanFailed(errorCode: Int) {
                log(TAG, WARN) { "onScanFailed(errorCode=$errorCode)" }
            }
        }

        val settings = ScanSettings.Builder().apply {
            setScanMode(mode)
            if (adapter.isOffloadedScanBatchingSupported) {
                setReportDelay(100)
            } else {
                log(TAG, WARN) { "isOffloadedScanBatchingSupported=false" }
            }
        }.build()

        if (adapter.isOffloadedFilteringSupported) {
            scanner.startScan(filter.toList(), settings, callback)
            log(TAG, VERBOSE) { "BleScanner started (filter=$filter, settings=$settings)" }
        } else {
            log(TAG, WARN) { "isOffloadedFilteringSupported=false" }
            scanner.startScan(callback)
            log(TAG, VERBOSE) { "BleScanner started" }
        }

        awaitClose {
            log(TAG, INFO) { "BleScanner stopped" }
            scanner.stopScan(callback)
        }
    }
        .map { origs ->
            val fakeDevices = mutableListOf<BleScanResult>()
            if (debugSettings.showFakeData.value) {
                // AirPods Pro
                BleScanResult(
                    address = "78:73:AF:B4:85:5E",
                    rssi = -48,
                    generatedAtNanos = SystemClockWrap.elapsedRealtimeNanos + 100,
                    manufacturerSpecificData = mapOf(76 to "07 19 01 0E 20 75 AA B6 31 00 05 9C 5A A4 5D C0 2C A0 B4 6F B9 ED 8E CE 03 97 CA".hexToByteArray())
                ).run { fakeDevices.add(this) }
                // AirPods Gen1
                BleScanResult(
                    address = "4E:9E:D1:49:D2:6D",
                    rssi = -55,
                    generatedAtNanos = SystemClockWrap.elapsedRealtimeNanos + 200,
                    manufacturerSpecificData = mapOf(76 to "07 19 01 02 20 55 AF 56 31 00 06 6F E4 DF 10 AF 10 60 81 03 3B 76 D9 C7 11 22 88".hexToByteArray())
                ).run { fakeDevices.add(this) }
                // AirPods Max
                BleScanResult(
                    address = "7E:E5:C7:65:D2:B5",
                    rssi = -57,
                    generatedAtNanos = SystemClockWrap.elapsedRealtimeNanos + 300,
                    manufacturerSpecificData = mapOf(76 to "07 19 01 0A 20 02 05 80 04 0F 44 A7 60 9B F8 3C FD B1 D8 1C 61 EA 82 60 A3 2C 4E".hexToByteArray())
                ).run { fakeDevices.add(this) }
                // BeatsFlex
                BleScanResult(
                    address = "5E:9E:D1:49:D2:6D",
                    rssi = -59,
                    generatedAtNanos = SystemClockWrap.elapsedRealtimeNanos + 400,
                    manufacturerSpecificData = mapOf(76 to "07 19 01 10 20 0A F4 8F 00 01 00 C4 71 9F 9C EF A2 E3 BA 66 FE 1D 45 9F C9 2F A0".hexToByteArray())
                ).run { fakeDevices.add(this) }
                // Unknown Device
                BleScanResult(
                    address = "6E:9E:D1:49:D2:6D",
                    rssi = -60,
                    generatedAtNanos = SystemClockWrap.elapsedRealtimeNanos + 500,
                    manufacturerSpecificData = mapOf(76 to "07 19 01 FF 20 0A F4 8F 00 01 00 C4 71 9F 9C EF A2 E3 BA 66 FE 1D 45 9F C9 2F A0".hexToByteArray())
                ).run { fakeDevices.add(this) }
            }

            origs + fakeDevices
        }


    fun String.hexToByteArray(): ByteArray {
        val trimmed = this
            .replace(" ", "")
            .replace(">", "")
            .replace("<", "")
        require(trimmed.length % 2 == 0) { "Not a HEX string" }
        return trimmed.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    companion object {
        private val TAG = logTag("Bluetooth", "BleScanner")
    }
}