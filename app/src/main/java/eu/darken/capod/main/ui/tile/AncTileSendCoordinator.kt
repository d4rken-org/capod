package eu.darken.capod.main.ui.tile

import eu.darken.capod.common.bluetooth.BluetoothAddress
import eu.darken.capod.common.coroutine.AppScope
import eu.darken.capod.common.debug.logging.Logging.Priority.ERROR
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.pods.core.apple.aap.AapConnectionManager
import eu.darken.capod.pods.core.apple.aap.protocol.AapCommand
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Process-scoped trailing-edge debouncer for tile-driven `SetAncMode` commands.
 *
 * The QS panel collapses after each tap, destroying the [AncTileService] instance.
 * If the pending-job state lived on the service, taps across consecutive panel
 * sessions wouldn't cancel each other — every tap would fire a separate
 * `SetAncMode`, overwhelming the AAP verification loop and triggering
 * "Rejected after retry" storms that leave the device unresponsive until the
 * app restarts. Keeping the job here, on a `@Singleton`, means a tap in panel
 * session B can cancel the deferred send queued by panel session A.
 */
@Singleton
class AncTileSendCoordinator @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val aapManager: AapConnectionManager,
) {

    private val lock = Any()
    private val pendingJobs = mutableMapOf<BluetoothAddress, Job>()
    private val timeoutJobs = mutableMapOf<BluetoothAddress, Job>()

    private val _pendingModes = MutableStateFlow<Map<BluetoothAddress, AapSetting.AncMode.Value>>(emptyMap())
    val pendingModes: StateFlow<Map<BluetoothAddress, AapSetting.AncMode.Value>> = _pendingModes.asStateFlow()

    fun scheduleSetAncMode(
        address: BluetoothAddress,
        mode: AapSetting.AncMode.Value,
        debounce: Duration,
        timeout: Duration = 5.seconds,
    ) {
        synchronized(lock) {
            val replacing = pendingJobs[address]?.isActive == true
            log(TAG, VERBOSE) { "scheduleSetAncMode($mode, addr=$address, debounce=$debounce, replacingPending=$replacing)" }
            _pendingModes.value = _pendingModes.value + (address to mode)

            pendingJobs.remove(address)?.cancel()
            timeoutJobs.remove(address)?.cancel()

            pendingJobs[address] = appScope.launch {
                delay(debounce)
                log(TAG, VERBOSE) { "debounce elapsed, dispatching SetAncMode($mode) to AAP for $address" }
                try {
                    aapManager.sendCommand(address, AapCommand.SetAncMode(mode))
                    log(TAG, VERBOSE) { "sent SetAncMode($mode) to $address" }
                } catch (e: CancellationException) {
                    log(TAG, VERBOSE) { "send for $mode cancelled (newer tap superseded it)" }
                    throw e
                } catch (e: Exception) {
                    log(TAG, ERROR) { "sendCommand failed: ${e.asLog()}" }
                    clearPendingTarget(address, mode)
                }
            }

            timeoutJobs[address] = appScope.launch {
                delay(timeout)
                if (clearPendingTargetFromTimeout(address, mode)) {
                    log(TAG, VERBOSE) { "pending tile target $mode timed out before device confirmation" }
                }
            }
        }
    }

    internal fun applyPendingTarget(state: AncTileState): AncTileState {
        val active = state as? AncTileState.Active ?: return state
        val address = active.deviceAddress ?: return active
        val target = pendingModes.value[address] ?: return active

        if (target !in active.visible) return active

        if (active.isConfirmed(target)) return active

        return active.copy(pending = target)
    }

    internal fun acknowledgeDeviceState(state: AncTileState) {
        val active = state as? AncTileState.Active ?: return
        val address = active.deviceAddress ?: return
        val target = pendingModes.value[address] ?: return

        if (target !in active.visible || active.isConfirmed(target)) {
            clearPendingTarget(address, target)
        }
    }

    private fun AncTileState.Active.isConfirmed(target: AapSetting.AncMode.Value): Boolean =
        pending == target || (current == target && pending == null)

    private fun clearPendingTarget(
        address: BluetoothAddress,
        expectedMode: AapSetting.AncMode.Value,
    ): Boolean = synchronized(lock) {
        val current = _pendingModes.value[address] ?: return@synchronized false
        if (current != expectedMode) return@synchronized false

        _pendingModes.value = _pendingModes.value - address
        pendingJobs.remove(address)?.cancel()
        timeoutJobs.remove(address)?.cancel()
        true
    }

    private fun clearPendingTargetFromTimeout(
        address: BluetoothAddress,
        expectedMode: AapSetting.AncMode.Value,
    ): Boolean = synchronized(lock) {
        val current = _pendingModes.value[address] ?: return@synchronized false
        if (current != expectedMode) return@synchronized false

        _pendingModes.value = _pendingModes.value - address
        pendingJobs.remove(address)?.cancel()
        timeoutJobs.remove(address)
        true
    }

    companion object {
        private val TAG = logTag("Tile", "Anc", "Coord")
    }
}
