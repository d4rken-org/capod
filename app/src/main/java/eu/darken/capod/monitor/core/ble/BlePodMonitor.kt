package eu.darken.capod.monitor.core.ble

import android.bluetooth.le.ScanFilter
import eu.darken.capod.common.TimeSource
import eu.darken.capod.common.bluetooth.BleScanResult
import eu.darken.capod.common.bluetooth.BleScanner
import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.bluetooth.ScannerMode
import eu.darken.capod.common.bluetooth.onlyNewAndUnique
import eu.darken.capod.common.coroutine.AppScope
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
import eu.darken.capod.pods.core.apple.ble.devices.DualApplePods
import eu.darken.capod.pods.core.apple.ble.protocol.ProximityPairing
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import eu.darken.capod.profiles.core.currentProfiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val bleScanModeController: BleScanModeController,
    private val podFactory: PodFactory,
    private val timeSource: TimeSource,
    private val generalSettings: GeneralSettings,
    bluetoothManager: BluetoothManager2,
    private val permissionTool: PermissionTool,
    private val profilesRepo: DeviceProfilesRepo,
) {

    private val deviceCache = mutableMapOf<BlePodSnapshot.Id, BlePodSnapshot>()
    private val cacheLock = Mutex()

    /**
     * Drops all cached observations. The troubleshooter calls this between probe attempts so a
     * previous compat combo's cached devices (kept up to [STALE_DEVICE_TIMEOUT]) can't leak into
     * the next attempt and falsely satisfy it — including via [preferCaseContextPod], which would
     * otherwise hand back a stale snapshot whose timestamp predates the fresh scan.
     */
    suspend fun clearDeviceCache() = cacheLock.withLock {
        log(TAG) { "clearDeviceCache()" }
        deviceCache.clear()
    }

    /**
     * Ephemeral override that disables the proximity-pairing scan filter so
     * the troubleshooter can collect raw BLE broadcasts. Resets to false on
     * every process start; the troubleshooter is the only writer.
     */
    private val unfilteredOverride = MutableStateFlow(false)

    fun setUnfilteredOverride(enabled: Boolean) {
        unfilteredOverride.value = enabled
    }

    /**
     * Ephemeral override for the three BLE compatibility options. The troubleshooter uses this to
     * probe combinations without writing the user's persisted settings: while set, it fully replaces
     * the persisted compat values for the active scan. Resets to null on every process start; the
     * troubleshooter is the only writer and always clears it when finished, so clearing it restores
     * the user's original settings for free.
     */
    private val compatOverride = MutableStateFlow<CompatOverride?>(null)

    fun setCompatOverride(override: CompatOverride?) {
        log(TAG) { "setCompatOverride($override)" }
        compatOverride.value = override
    }

    data class CompatOverride(
        val offloadedFilteringDisabled: Boolean,
        val offloadedBatchingDisabled: Boolean,
        val indirectCallback: Boolean,
    )

    /** Persisted compat settings, transparently replaced by [compatOverride] while it is set. */
    private val effectiveCompat: Flow<CompatOverride> = combine(
        compatOverride,
        generalSettings.isOffloadedFilteringDisabled.flow,
        generalSettings.isOffloadedBatchingDisabled.flow,
        generalSettings.useIndirectScanResultCallback.flow,
    ) { override, filteringDisabled, batchingDisabled, indirectCallback ->
        override ?: CompatOverride(
            offloadedFilteringDisabled = filteringDisabled,
            offloadedBatchingDisabled = batchingDisabled,
            indirectCallback = indirectCallback,
        )
    }

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
                ) { "PodMonitor failed due to missing permission, rechecking and retrying: ${cause.asLog()}" }
                permissionTool.recheck()
                delay(3000)
                true
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
        bleScanModeController.scannerMode,
        unfilteredOverride,
        effectiveCompat,
    ) { scannermode, showUnfiltered, compat ->
        ScannerOptions(
            scannerMode = scannermode,
            showUnfiltered = showUnfiltered,
            offloadedFilteringDisabled = compat.offloadedFilteringDisabled,
            offloadedBatchingDisabled = compat.offloadedBatchingDisabled,
            disableDirectCallback = compat.indirectCallback,
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

            flow {
                emitAll(
                    bleScanner.scan(
                        filters = filters,
                        scannerMode = options.scannerMode,
                        disableOffloadFiltering = options.offloadedFilteringDisabled,
                        disableOffloadBatching = options.offloadedBatchingDisabled,
                        disableDirectScanCallback = options.disableDirectCallback,
                    )
                )
            }.catch { cause ->
                if (cause is SecurityException) {
                    log(TAG, Logging.Priority.WARN) {
                        "BLE scanner failed due to missing permission, rechecking permissions: ${cause.asLog()}"
                    }
                    permissionTool.recheck()
                    emit(emptyList())
                } else {
                    throw cause
                }
            }
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

        newPods.map { it.device }.forEach { newPod ->
            val existing = pods[newPod.identifier]
            val preferred = if (existing != null) {
                preferCaseContextPod(existing, newPod)
            } else {
                newPod
            }
            deviceCache[preferred.identifier] = preferred
            pods[preferred.identifier] = preferred
        }

        return pods
    }

    /**
     * When two scan results in the same batch map to the same device identity,
     * prefer the one broadcasting from inside the case (has case context bits set).
     * It carries authoritative case state and battery data.
     */
    private fun preferCaseContextPod(
        existing: BlePodSnapshot,
        incoming: BlePodSnapshot,
    ): BlePodSnapshot {
        val existingDual = existing as? DualApplePods
        val incomingDual = incoming as? DualApplePods
        if (existingDual == null || incomingDual == null) return incoming

        return when {
            existingDual.hasCaseContext && !incomingDual.hasCaseContext -> existing
            !existingDual.hasCaseContext && incomingDual.hasCaseContext -> incoming
            else -> incoming
        }
    }

    companion object {
        private val TAG = logTag("Monitor", "PodMonitor")
        private val STALE_DEVICE_TIMEOUT = Duration.ofSeconds(20)
        private val STALE_EVICTION_INTERVAL = Duration.ofSeconds(10)
    }
}
