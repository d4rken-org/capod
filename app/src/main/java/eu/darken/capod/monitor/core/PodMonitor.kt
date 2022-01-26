package eu.darken.capod.monitor.core

import eu.darken.capod.common.bluetooth.BleScanner
import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.coroutine.AppScope
import eu.darken.capod.common.debug.logging.Logging.Priority.*
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.replayingShare
import eu.darken.capod.main.core.GeneralSettings
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.PodFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
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
    private val bluetoothManager: BluetoothManager2,
    @AppScope private val appScope: CoroutineScope
) {

    private val deviceCache = mutableMapOf<PodDevice.Id, PodDevice>()
    private val cacheLock = Mutex()

    val devices: Flow<List<PodDevice>> = bluetoothManager.isBluetoothEnabled
        .flatMapLatest { isBluetoothEnabled ->
            if (isBluetoothEnabled) {
                log(TAG) { "Bluetooth is enabled" }
                generalSettings.scannerMode.flow.flatMapLatest { bleScanner.scan(scannerMode = it) }
            } else {
                log(TAG, WARN) { "Bluetooth is currently disabled" }
                flowOf(null)
            }
        }
        .map { result ->
            // For each address we only want the newest result, upstream may batch data
            result?.groupBy { it.address }
                ?.values
                ?.map { sameAdrDevs ->
                    val newest = sameAdrDevs.maxByOrNull { it.generatedAtNanos }!!
                    sameAdrDevs.minus(newest).let {
                        if (it.isNotEmpty()) log(TAG, VERBOSE) { "Discarding stale results: $it" }
                    }
                    newest
                }
        }
        .map { scanResults ->
            val pods = mutableMapOf<PodDevice.Id, PodDevice>()

            cacheLock.withLock {
                if (scanResults == null) {
                    log(TAG) { "Null result, Bluetooth is disabled." }
                    deviceCache.clear()
                    return@map emptyList()
                }

                val newPods = scanResults.mapNotNull { podFactory.createPod(it) }
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

            val now = Instant.now()
            val minimumSignalQuality = generalSettings.minimumSignalQuality.value
            val mainDeviceModel = generalSettings.mainDeviceModel.value

            val aboveTresholdSorted = pods.values
                .filter { it.signalQuality > minimumSignalQuality }
                .sortedWith(
                    compareByDescending<PodDevice> { it.model == mainDeviceModel && it.model != PodDevice.Model.UNKNOWN }
                        .thenBy {
                            val age = Duration.between(it.lastSeenAt, now).seconds
                            if (age < 3) 0L else age
                        }
                        .thenByDescending { it.signalQuality }
                )
            val belowThresholdSorted = pods.values
                .filter { it.signalQuality <= minimumSignalQuality }
                .sortedWith(
                    compareBy<PodDevice> { Duration.between(it.lastSeenAt, now) }.thenByDescending { it.signalQuality }
                )

            val presorted = (aboveTresholdSorted + belowThresholdSorted)
            val main = presorted.determineMainDevice()
            presorted.sortedByDescending { it == main }
        }
        .onStart { emit(emptyList()) }
        .retryWhen { cause, attempt ->
            log(TAG, WARN) { "PodMonitor failed (attempt=$attempt), will retry: ${cause.asLog()}" }
            delay(3000)
            true
        }
        .replayingShare(appScope)

    val mainDevice: Flow<PodDevice?>
        get() = devices
            .map { it.determineMainDevice() }
            .replayingShare(appScope)

    private fun List<PodDevice>.determineMainDevice(): PodDevice? =
        this.firstOrNull()?.let {
            val mainDeviceModel = generalSettings.mainDeviceModel.value
            when {
                it.model == PodDevice.Model.UNKNOWN -> null
                mainDeviceModel != PodDevice.Model.UNKNOWN && it.model != mainDeviceModel -> null
                it.signalQuality <= generalSettings.minimumSignalQuality.value -> null
                else -> it
            }
        }

    companion object {
        private val TAG = logTag("Monitor", "PodMonitor")
    }
}