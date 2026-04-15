package eu.darken.capod.pods.core.apple.aap

import android.bluetooth.BluetoothDevice
import eu.darken.capod.common.TimeSource
import eu.darken.capod.common.bluetooth.BluetoothAddress
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.bluetooth.l2cap.L2capSocketFactory
import eu.darken.capod.common.coroutine.AppScope
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.protocol.AapCommand
import eu.darken.capod.pods.core.apple.aap.protocol.AapDeviceProfile
import eu.darken.capod.pods.core.apple.aap.protocol.KeyExchangeResult
import eu.darken.capod.pods.core.apple.aap.protocol.StemPressEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton managing all AAP L2CAP connections.
 * Keyed by [BluetoothAddress] (stable across scan gaps and app restarts for bonded devices).
 * Connections are owned internally — consumers interact via [sendCommand] and observe via [allStates].
 */
@Singleton
class AapConnectionManager @Inject constructor(
    private val socketFactory: L2capSocketFactory,
    @AppScope private val scope: CoroutineScope,
    private val timeSource: TimeSource,
) {
    companion object {
        private val TAG = logTag("AAP", "Manager")
    }

    private val mutex = Mutex()
    private val connections = mutableMapOf<BluetoothAddress, AapConnection>()
    private val connectionJobs = mutableMapOf<BluetoothAddress, Job>()
    private val intentionalDisconnects = mutableSetOf<BluetoothAddress>()

    private val _allStates = MutableStateFlow<Map<BluetoothAddress, AapPodState>>(emptyMap())
    val allStates: StateFlow<Map<BluetoothAddress, AapPodState>> = _allStates.asStateFlow()

    private val _disconnectEvents = MutableSharedFlow<BluetoothAddress>(extraBufferCapacity = 16)
    val disconnectEvents: SharedFlow<BluetoothAddress> = _disconnectEvents.asSharedFlow()

    /** Emits when a connection receives private keys (IRK/ENC) from the device. */
    private val _keysReceived = MutableSharedFlow<Pair<BluetoothAddress, KeyExchangeResult>>(extraBufferCapacity = 16)
    val keysReceived: SharedFlow<Pair<BluetoothAddress, KeyExchangeResult>> = _keysReceived.asSharedFlow()

    /** Emits transient stem press events from any connected device. */
    private val _stemPressEvents = MutableSharedFlow<Pair<BluetoothAddress, StemPressEvent>>(extraBufferCapacity = 32)
    val stemPressEvents: SharedFlow<Pair<BluetoothAddress, StemPressEvent>> = _stemPressEvents.asSharedFlow()

    fun deviceState(address: BluetoothAddress) = _allStates.map { it[address] }

    suspend fun connect(
        address: BluetoothAddress,
        device: BluetoothDevice,
        model: PodModel,
    ) = mutex.withLock {
        if (connections.containsKey(address)) {
            log(TAG) { "Already connected to $address" }
            return
        }

        // Clear stale intentional-disconnect flag from previous connection lifecycle
        intentionalDisconnects.remove(address)

        val profile = AapDeviceProfile.Companion.forModel(model)
        val connection = AapConnection(device, profile, socketFactory, timeSource = timeSource)
        connections[address] = connection

        try {
            connection.connect(scope)
        } catch (e: Exception) {
            connections.remove(address)
            throw e
        }

        // Collect connection state and propagate live updates to allStates.
        // The coroutine cancels itself after handling DISCONNECTED,
        // which also cancels the child key-forwarding coroutine.
        connectionJobs[address] = scope.launch {
            // Forward private keys from this connection (child coroutine)
            launch {
                connection.keysReceived.collect { keys ->
                    _keysReceived.tryEmit(address to keys)
                }
            }

            // Forward stem press events from this connection (child coroutine)
            launch {
                connection.stemPressEvents.collect { event ->
                    _stemPressEvents.tryEmit(address to event)
                }
            }

            connection.state.collect { podState ->
                if (podState.connectionState == AapPodState.ConnectionState.DISCONNECTED) {
                    log(TAG) { "Connection to $address disconnected" }
                    val wasIntentional = mutex.withLock {
                        connections.remove(address)
                        connectionJobs.remove(address)
                        connection.disconnect()
                        _allStates.update { it - address }
                        val intentional = address in intentionalDisconnects
                        intentionalDisconnects.remove(address)
                        intentional
                    }

                    if (!wasIntentional) {
                        _disconnectEvents.tryEmit(address)
                    }

                    // End this collector coroutine (also cancels child key-forwarding coroutine)
                    cancel()
                } else {
                    _allStates.update { it + (address to podState) }
                }
            }
        }
    }

    suspend fun disconnect(address: BluetoothAddress) = mutex.withLock {
        intentionalDisconnects.add(address)
        val connection = connections.remove(address) ?: run {
            intentionalDisconnects.remove(address)
            return
        }
        connectionJobs.remove(address)?.cancel()
        connection.disconnect()
        _allStates.update { it - address }
    }

    suspend fun sendCommand(address: BluetoothAddress, command: AapCommand) {
        val connection = mutex.withLock { connections[address] }
            ?: throw IllegalStateException("No connection for $address")
        connection.send(command)
    }
}
