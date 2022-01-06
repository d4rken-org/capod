package eu.darken.capod.monitor.core

import android.bluetooth.le.ScanSettings
import eu.darken.capod.common.bluetooth.BleScanner
import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.main.core.GeneralSettings
import eu.darken.capod.main.core.ScannerMode
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.PodFactory
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PodMonitor @Inject constructor(
    private val bleScanner: BleScanner,
    private val podFactory: PodFactory,
    private val generalSettings: GeneralSettings,
    private val bluetoothManager: BluetoothManager2
) {

    private val deviceCache = mutableMapOf<PodDevice.Id, PodDevice>()
    private val cacheLock = Mutex()

    val devices: Flow<List<PodDevice>> = generalSettings.scannerMode.flow
        .flatMapLatest {
            val mode = when (it) {
                ScannerMode.LOW_POWER -> ScanSettings.SCAN_MODE_LOW_POWER
                ScannerMode.BALANCED -> ScanSettings.SCAN_MODE_BALANCED
                ScannerMode.LOW_LATENCY -> ScanSettings.SCAN_MODE_LOW_LATENCY
            }
            bluetoothManager.isBluetoothEnabled.flatMapLatest { isBluetoothEnabled ->
                if (isBluetoothEnabled) {
                    bleScanner.scan(
                        mode = mode
                    )
                } else {
                    log(TAG, WARN) { "Bluetooth is current disabled" }
                    emptyFlow()
                }
            }
        }
        .map { result ->
            // For each address we only want the newest result, upstream may batch data
            result.groupBy { it.address }
                .values
                .map { sameAdrDevs ->
                    val newest = sameAdrDevs.maxByOrNull { it.generatedAtNanos }!!
                    log(TAG, VERBOSE) { "Discarding stale results: ${sameAdrDevs.minus(newest)}" }
                    newest
                }
        }
        .onStart { emptyList<PodDevice>() }
        .map { scanResults ->
            val newPods = scanResults
                .mapNotNull { podFactory.createPod(it) }

            val pods = mutableMapOf<PodDevice.Id, PodDevice>()

            cacheLock.withLock {
                val now = Instant.now()
                deviceCache.toList().forEach { (key, value) ->
                    if (Duration.between(value.lastSeenAt, now) > Duration.ofSeconds(20)) {
                        log(TAG, VERBOSE) { "Removing stale device from cache: $value" }
                        deviceCache.remove(key)
                    }
                }

                pods.putAll(deviceCache)

                newPods.forEach {
                    deviceCache[it.identifier] = it
                    pods[it.identifier] = it
                }
            }

            pods.values.sortedByDescending { it.rssi }
        }

    val mainDevice: Flow<PodDevice?>
        get() = devices.map { devices ->
            devices.maxByOrNull { it.rssi }?.let {
                if (it.rssi > -85) it else null
            }
        }

    companion object {
        private val TAG = logTag("Monitor", "PodMonitor")
    }
}