package eu.darken.capod.pods.core.apple.protocol.aap

import android.bluetooth.BluetoothDevice
import android.util.Log
import eu.darken.capod.common.bluetooth.BluetoothAddress
import eu.darken.capod.common.bluetooth.l2cap.L2capSocketFactory
import eu.darken.capod.pods.core.PodModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
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
) {
    companion object {
        private const val TAG = "AapConnectionMgr"
    }

    private val connections = mutableMapOf<BluetoothAddress, AapConnection>()
    private val _allStates = MutableStateFlow<Map<BluetoothAddress, AapPodState>>(emptyMap())
    val allStates: StateFlow<Map<BluetoothAddress, AapPodState>> = _allStates.asStateFlow()

    fun deviceState(address: BluetoothAddress): Flow<AapPodState?> =
        _allStates.map { it[address] }

    suspend fun connect(
        address: BluetoothAddress,
        device: BluetoothDevice,
        model: PodModel,
    ) {
        if (connections.containsKey(address)) {
            Log.d(TAG, "Already connected to $address")
            return
        }

        val profile = AapDeviceProfile.forModel(model)
        val connection = AapConnection(device, profile, socketFactory)
        connections[address] = connection

        try {
            connection.connect()
        } catch (e: Exception) {
            connections.remove(address)
            throw e
        }

        // Observe connection state and propagate to allStates
        // Note: in production this would use a coroutine scope to collect the flow
        updateStates()
    }

    suspend fun disconnect(address: BluetoothAddress) {
        val connection = connections.remove(address) ?: return
        connection.disconnect()
        updateStates()
    }

    suspend fun sendCommand(address: BluetoothAddress, command: AapCommand) {
        val connection = connections[address]
            ?: throw IllegalStateException("No connection for $address")
        connection.send(command)
    }

    private fun updateStates() {
        _allStates.value = connections.mapValues { (_, conn) -> conn.state.value }
    }
}
