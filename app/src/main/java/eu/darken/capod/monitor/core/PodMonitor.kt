package eu.darken.capod.monitor.core

import android.bluetooth.le.ScanFilter
import eu.darken.capod.common.bluetooth.BleScanner
import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.bluetooth.ScannerMode
import eu.darken.capod.common.bluetooth.onlyNewAndUnique
import eu.darken.capod.common.coroutine.AppScope
import eu.darken.capod.common.debug.DebugSettings
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.replayingShare
import eu.darken.capod.common.flow.throttleLatest
import eu.darken.capod.main.core.GeneralSettings
import eu.darken.capod.main.core.PermissionTool
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.PodFactory
import eu.darken.capod.pods.core.apple.protocol.ProximityPairing
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import eu.darken.capod.profiles.core.currentProfiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PodMonitor @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val bleScanner: BleScanner,
    private val podFactory: PodFactory,
    private val generalSettings: GeneralSettings,
    bluetoothManager: BluetoothManager2,
    private val debugSettings: DebugSettings,
    private val podDeviceCache: PodDeviceCache,
    permissionTool: PermissionTool,
    private val profilesRepo: DeviceProfilesRepo,
) {

    private val deviceCache = mutableMapOf<PodDevice.Id, PodDevice>()
    private val cacheLock = Mutex()

    val devices: Flow<List<PodDevice>> = combine(
        permissionTool.missingPermissions,
        bluetoothManager.isBluetoothEnabled
    ) { missingPermissions, isBluetoothEnabled ->
        log(TAG) { "devices: missingPermissions=$missingPermissions, isBluetoothEnabled=$isBluetoothEnabled" }
        // We just want to retrigger if permissions change.
        isBluetoothEnabled
    }
        .flatMapLatest { isReady ->
            if (!isReady) {
                log(TAG, WARN) { "Bluetooth is not ready" }
                flowOf(null)
            } else {
                createBleScanner()
            }
        }
        .map { results -> results?.mapNotNull { podFactory.createPod(it) } }
        .map { processWithCache(it).values }
        .flatMapLatest { devices ->
            flowOf(sortPodsToInterest(devices))
        }
        .retryWhen { cause, attempt ->
            log(TAG, WARN) { "PodMonitor failed (attempt=$attempt), will retry: ${cause.asLog()}" }
            delay(3000)
            true
        }
        .onStart { emit(emptyList()) }
        .replayingShare(appScope)

    private suspend fun sortPodsToInterest(devices: Collection<PodDevice>): List<PodDevice> {
        val now = Instant.now()
        val profiles = profilesRepo.currentProfiles() ?: emptyList()

        return devices.sortedWith(
            compareBy<PodDevice> { device ->
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
                    log(TAG, WARN) { "Using unfiltered scan mode" }
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
    ): Map<PodDevice.Id, PodDevice> = cacheLock.withLock {
        if (newPods == null) {
            log(TAG) { "Null result, Bluetooth is disabled." }
            deviceCache.clear()
            return emptyMap()
        }

        val now = Instant.now()
        deviceCache.toList().forEach { (key, value) ->
            if (Duration.between(value.seenLastAt, now) > Duration.ofSeconds(20)) {
                log(TAG, VERBOSE) { "Removing stale device from cache: $value" }
                deviceCache.remove(key)
            }
        }

        val pods = mutableMapOf<PodDevice.Id, PodDevice>()

        pods.putAll(deviceCache)

        newPods.map { it.device }.forEach {
            deviceCache[it.identifier] = it
            pods[it.identifier] = it
        }

        newPods
            .mapNotNull {
                val profileId = it.device.meta.profile?.id ?: return@mapNotNull null
                profileId to it.device.scanResult
            }
            .toMap()
            .run {                podDeviceCache.saveAll(this)            }
        return pods
    }

    suspend fun getDeviceForProfile(profileId: String): PodDevice? {
        log(TAG) { "getDeviceForProfile(profileId=$profileId)" }

        val liveDevice = devices.firstOrNull()?.firstOrNull { device ->
            device.meta.profile?.id == profileId
        }
        if (liveDevice != null) {
            log(TAG) { "Found live device for profile $profileId: $liveDevice" }
            return liveDevice
        }

        val cachedDevice = podDeviceCache.load(profileId)?.let {
            podFactory.createPod(it)?.device
        }
        log(TAG) { "Cached device for profile $profileId: $cachedDevice" }
        return cachedDevice
    }

    companion object {
        private val TAG = logTag("Monitor", "PodMonitor")
    }
}