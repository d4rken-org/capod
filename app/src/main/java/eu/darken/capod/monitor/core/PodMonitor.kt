package eu.darken.capod.monitor.core

import android.bluetooth.le.ScanFilter
import eu.darken.capod.common.bluetooth.BleScanResult
import eu.darken.capod.common.bluetooth.BleScanner
import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.coroutine.AppScope
import eu.darken.capod.common.debug.autoreport.DebugSettings
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.replayingShare
import eu.darken.capod.main.core.GeneralSettings
import eu.darken.capod.main.core.ScannerMode
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.PodFactory
import eu.darken.capod.pods.core.apple.protocol.ProximityPairing
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
    private val debugSettings: DebugSettings,
    @AppScope private val appScope: CoroutineScope
) {

    private val deviceCache = mutableMapOf<PodDevice.Id, PodDevice>()
    private val cacheLock = Mutex()

    private suspend fun List<BleScanResult>.preFilterAndMap(
        scannerMode: ScannerMode
    ): List<PodDevice> = this
        .groupBy { it.address }
        .values
        .map { sameAdrDevs ->
            // For each address we only want the newest result, upstream may batch data
            val newest = sameAdrDevs.maxByOrNull { it.generatedAtNanos }!!
            sameAdrDevs.minus(newest).let {
                if (it.isNotEmpty()) log(TAG, VERBOSE) { "Discarding stale results: $it" }
            }
            newest
        }
        .mapNotNull { podFactory.createPod(it) }

    val devices: Flow<List<PodDevice>> = bluetoothManager.isBluetoothEnabled
        .flatMapLatest { isBluetoothEnabled ->
            if (isBluetoothEnabled) {
                log(TAG) { "Bluetooth is enabled" }
                combine(
                    generalSettings.scannerMode.flow,
                    generalSettings.compatibilityMode.flow,
                    debugSettings.showUnfiltered.flow
                ) { scannerMode, compatMode, unfiltered ->
                    Triple(scannerMode, compatMode, unfiltered)
                }.flatMapLatest { (mode, compat, unfiltered) ->
                    val filters = if (unfiltered) {
                        setOf(getUnfilteredFilter())
                    } else {
                        ProximityPairing.getBleScanFilter()
                    }
                    bleScanner.scan(
                        filters = filters,
                        scannerMode = mode,
                        compatMode = compat,
                    ).map { it.preFilterAndMap(mode) }
                }
            } else {
                log(TAG, WARN) { "Bluetooth is currently disabled" }
                flowOf(null)
            }
        }
        .map { newPods ->
            val pods = mutableMapOf<PodDevice.Id, PodDevice>()

            cacheLock.withLock {
                if (newPods == null) {
                    log(TAG) { "Null result, Bluetooth is disabled." }
                    deviceCache.clear()
                    return@map emptyList()
                }

                val now = Instant.now()
                deviceCache.toList().forEach { (key, value) ->
                    if (Duration.between(value.seenLastAt, now) > Duration.ofSeconds(20)) {
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

            val presorted = pods.values.sortPodsToInterest()
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

    private fun Collection<PodDevice>.sortPodsToInterest(): List<PodDevice> = this.let { devices ->
        val now = Instant.now()

        return@let devices.sortedWith(
            compareByDescending<PodDevice> { true }
                .thenBy {
                    val age = Duration.between(it.seenLastAt, now).seconds
                    if (age < 5) 0L else (age / 3L).toLong()
                }
                .thenByDescending { it.signalQuality }
                .thenByDescending { (it.seenCounter / 10) }

        )
    }

    private fun List<PodDevice>.determineMainDevice(): PodDevice? = this
        .sortPodsToInterest()
        .let { devices ->
            val mainDeviceModel = generalSettings.mainDeviceModel.value

            val presorted = devices.sortedByDescending {
                it.model == mainDeviceModel && it.model != PodDevice.Model.UNKNOWN
            }

            return@let presorted.firstOrNull()?.let { candidate ->
                when {
                    candidate.model == PodDevice.Model.UNKNOWN -> null
                    mainDeviceModel != PodDevice.Model.UNKNOWN && candidate.model != mainDeviceModel -> null
                    candidate.signalQuality <= generalSettings.minimumSignalQuality.value -> null
                    else -> candidate
                }
            }
        }


    private fun getUnfilteredFilter(): ScanFilter {
        return ScanFilter.Builder().build()
    }

    companion object {
        private val TAG = logTag("Monitor", "PodMonitor")
    }
}