package eu.darken.capod.monitor.core.ble

import android.bluetooth.le.ScanFilter
import eu.darken.capod.common.TimeSource
import eu.darken.capod.common.bluetooth.BleScanResult
import eu.darken.capod.common.bluetooth.BleScanner
import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.bluetooth.ScannerMode
import eu.darken.capod.common.bluetooth.onlyNewAndUnique
import eu.darken.capod.common.coroutine.AppScope
import eu.darken.capod.common.debug.DebugSettings
import eu.darken.capod.common.debug.logging.Logging
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.replayingShare
import eu.darken.capod.common.flow.throttleLatest
import eu.darken.capod.main.core.GeneralSettings
import eu.darken.capod.main.core.PermissionTool
import eu.darken.capod.pods.core.apple.ble.BlePodSnapshot
import eu.darken.capod.pods.core.apple.ble.PodFactory
import eu.darken.capod.pods.core.apple.ble.protocol.ProximityPairing
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import eu.darken.capod.profiles.core.currentProfiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlePodMonitor @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val bleScanner: BleScanner,
    private val podFactory: PodFactory,
    private val timeSource: TimeSource,
    private val generalSettings: GeneralSettings,
    bluetoothManager: BluetoothManager2,
    private val debugSettings: DebugSettings,
    permissionTool: PermissionTool,
    private val profilesRepo: DeviceProfilesRepo,
) {

    private val deviceCache = mutableMapOf<BlePodSnapshot.Id, BlePodSnapshot>()
    private val cacheLock = Mutex()

    val devices: Flow<List<BlePodSnapshot>> = combine(
        permissionTool.missingScanPermissions,
        bluetoothManager.isBluetoothEnabled
    ) { missingScanPermissions, isBluetoothEnabled ->
        log(TAG) { "devices: missingScanPermissions=$missingScanPermissions, isBluetoothEnabled=$isBluetoothEnabled" }
        missingScanPermissions.isEmpty() && isBluetoothEnabled
    }
        .flatMapLatest { isReady ->
            if (!isReady) {
                log(TAG, Logging.Priority.WARN) { "Bluetooth is not ready" }
                flowOf(null)
            } else {
                val staleEvictionTicker: Flow<Collection<BleScanResult>> = flow {
                    while (true) {
                        delay(STALE_EVICTION_INTERVAL.toMillis())
                        emit(emptyList())
                    }
                }
                merge(createBleScanner(), staleEvictionTicker)
            }
        }
        .map { results -> results?.mapNotNull { podFactory.createPod(it) } }
        .map { processWithCache(it).values }
        .flatMapLatest { devices ->
            flowOf(sortPodsToInterest(devices))
        }
        .retryWhen { cause, attempt ->
            if (cause is SecurityException) {
                log(
                    TAG,
                    Logging.Priority.WARN
                ) { "PodMonitor failed due to missing permission, not retrying: ${cause.asLog()}" }
                false
            } else {
                log(TAG, Logging.Priority.WARN) { "PodMonitor failed (attempt=$attempt), will retry: ${cause.asLog()}" }
                delay(3000)
                true
            }
        }
        .onStart { emit(emptyList()) }
        .replayingShare(appScope)

    private suspend fun sortPodsToInterest(devices: Collection<BlePodSnapshot>): List<BlePodSnapshot> {
        val now = timeSource.now()
        val profiles = profilesRepo.currentProfiles() ?: emptyList()

        return devices.sortedWith(
            compareBy<BlePodSnapshot> { device ->
                // Use profile position in list as priority (0 = highest priority)
                device.meta.profile?.let { profile ->
                    profiles.indexOfFirst { it.id == profile.id }.takeIf { it >= 0 } ?: Int.MAX_VALUE
                } ?: Int.MAX_VALUE
            }
                .thenBy {
                    val age = Duration.between(it.seenLastAt, now).seconds
                    if (age < 5) 0L else (age / 3L)
                }
                .thenByDescending { it.signalQuality }
                .thenByDescending { (it.seenCounter / 10) }
        )
    }

    private data class ScannerOptions(
        val scannerMode: ScannerMode,
        val showUnfiltered: Boolean,
        val offloadedFilteringDisabled: Boolean,
        val offloadedBatchingDisabled: Boolean,
        val disableDirectCallback: Boolean,
    )

    private fun createBleScanner() = combine(
        generalSettings.scannerMode.flow,
        debugSettings.showUnfiltered.flow,
        generalSettings.isOffloadedBatchingDisabled.flow,
        generalSettings.isOffloadedFilteringDisabled.flow,
        generalSettings.useIndirectScanResultCallback.flow,
    ) {
            scannermode,
            showUnfiltered,
            isOffloadedBatchingDisabled,
            isOffloadedFilteringDisabled,
            useIndirectScanResultCallback,
        ->
        ScannerOptions(
            scannerMode = scannermode,
            showUnfiltered = showUnfiltered,
            offloadedFilteringDisabled = isOffloadedFilteringDisabled,
            offloadedBatchingDisabled = isOffloadedBatchingDisabled,
            disableDirectCallback = useIndirectScanResultCallback,
        )
    }
        .throttleLatest(1000)
        .flatMapLatest { options ->
            val filters = when {
                options.showUnfiltered -> {
                    log(TAG, Logging.Priority.WARN) { "Using unfiltered scan mode" }
                    setOf(ScanFilter.Builder().build())
                }

                else -> ProximityPairing.getBleScanFilter()
            }

            bleScanner.scan(
                filters = filters,
                scannerMode = options.scannerMode,
                disableOffloadFiltering = options.offloadedFilteringDisabled,
                disableOffloadBatching = options.offloadedBatchingDisabled,
                disableDirectScanCallback = options.disableDirectCallback,
            )
        }
        .map { it.onlyNewAndUnique() }

    private suspend fun processWithCache(
        newPods: List<PodFactory.Result>?
    ): Map<BlePodSnapshot.Id, BlePodSnapshot> = cacheLock.withLock {
        if (newPods == null) {
            log(TAG) { "Null result, Bluetooth is disabled." }
            deviceCache.clear()
            return emptyMap()
        }

        val now = timeSource.now()
        deviceCache.toList().forEach { (key, value) ->
            if (Duration.between(value.seenLastAt, now) > STALE_DEVICE_TIMEOUT) {
                log(TAG, Logging.Priority.VERBOSE) { "Removing stale device from cache: $value" }
                deviceCache.remove(key)
            }
        }

        val pods = mutableMapOf<BlePodSnapshot.Id, BlePodSnapshot>()

        pods.putAll(deviceCache)

        newPods.map { it.device }.forEach {
            deviceCache[it.identifier] = it
            pods[it.identifier] = it
        }

        return pods
    }

    companion object {
        private val TAG = logTag("Monitor", "PodMonitor")
        private val STALE_DEVICE_TIMEOUT = Duration.ofSeconds(20)
        private val STALE_EVICTION_INTERVAL = Duration.ofSeconds(10)
    }
}
