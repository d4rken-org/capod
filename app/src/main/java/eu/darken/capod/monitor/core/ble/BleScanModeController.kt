package eu.darken.capod.monitor.core.ble

import eu.darken.capod.common.AppForegroundState
import eu.darken.capod.common.bluetooth.BluetoothAddress
import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.bluetooth.ScannerMode
import eu.darken.capod.common.coroutine.AppScope
import eu.darken.capod.common.debug.logging.Logging
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.replayingShare
import eu.darken.capod.common.flow.setupCommonEventHandlers
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleScanModeController @Inject constructor(
    @AppScope appScope: CoroutineScope,
    appForegroundState: AppForegroundState,
    profilesRepo: DeviceProfilesRepo,
    bluetoothManager: BluetoothManager2,
) {

    private val overrideCounts = MutableStateFlow<Map<ScannerMode, Int>>(emptyMap())
    private val activeOverride: Flow<ScannerMode?> = overrideCounts.map { it.topPriority() }

    val scannerMode: Flow<ScannerMode> = combine(
        activeOverride,
        appForegroundState.isForeground,
        profilesRepo.profiles,
        bluetoothManager.connectedDevices
            .onStart { emit(emptyList()) }
            .catch {
                log(TAG, Logging.Priority.WARN) { "connectedDevices failed, treating as empty: $it" }
                emit(emptyList())
            },
        bluetoothManager.bondedDeviceAddresses,
    ) { override, isForeground, profiles, connectedDevices, bondedAddresses ->
        resolveScannerMode(
            overrideMode = override,
            isForeground = isForeground,
            profileAddresses = profiles.mapNotNull { it.address }.toSet(),
            bondedAddresses = bondedAddresses,
            connectedAddresses = connectedDevices.map { it.address }.toSet(),
        )
    }
        .distinctUntilChanged()
        .onEach { log(TAG) { "Effective scanner mode: $it" } }
        .setupCommonEventHandlers(TAG) { "scannerMode" }
        .replayingShare(appScope)

    suspend fun <T> withTemporaryOverride(mode: ScannerMode, block: suspend () -> T): T {
        log(TAG) { "withTemporaryOverride($mode) acquire" }
        overrideCounts.update { it.adjust(mode, +1) }
        try {
            return block()
        } finally {
            overrideCounts.update { it.adjust(mode, -1) }
            log(TAG) { "withTemporaryOverride($mode) release" }
        }
    }

    private fun Map<ScannerMode, Int>.adjust(mode: ScannerMode, delta: Int): Map<ScannerMode, Int> {
        val current = this[mode] ?: 0
        val next = current + delta
        return when {
            next <= 0 -> this - mode
            else -> this + (mode to next)
        }
    }

    private fun Map<ScannerMode, Int>.topPriority(): ScannerMode? = OVERRIDE_PRIORITY.firstOrNull { containsKey(it) }

    companion object {
        private val TAG = logTag("Bluetooth", "ScannerMode")
        private val OVERRIDE_PRIORITY = listOf(ScannerMode.LOW_LATENCY, ScannerMode.BALANCED, ScannerMode.LOW_POWER)
    }
}

internal fun resolveScannerMode(
    overrideMode: ScannerMode?,
    isForeground: Boolean,
    profileAddresses: Set<BluetoothAddress>,
    bondedAddresses: Set<BluetoothAddress>,
    connectedAddresses: Set<BluetoothAddress>,
): ScannerMode {
    if (overrideMode != null) return overrideMode

    val connectedProfileAddresses = profileAddresses.normalized()
        .intersect(bondedAddresses.normalized())
        .intersect(connectedAddresses.normalized())

    return when {
        connectedProfileAddresses.isNotEmpty() -> ScannerMode.LOW_LATENCY
        isForeground -> ScannerMode.BALANCED
        else -> ScannerMode.LOW_POWER
    }
}

private fun Iterable<BluetoothAddress>.normalized(): Set<BluetoothAddress> = this
    .map { it.uppercase(Locale.US) }
    .toSet()
