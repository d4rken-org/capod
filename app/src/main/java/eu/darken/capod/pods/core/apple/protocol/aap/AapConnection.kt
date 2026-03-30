package eu.darken.capod.pods.core.apple.protocol.aap

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import eu.darken.capod.common.bluetooth.l2cap.L2capSocketFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Manages a single AAP L2CAP connection to a device.
 * Internal — not exposed outside [AapConnectionManager].
 */
@SuppressLint("MissingPermission")
internal class AapConnection(
    private val device: BluetoothDevice,
    private val profile: AapDeviceProfile,
    private val socketFactory: L2capSocketFactory,
    private val psm: Int = 0x1001,
) {
    companion object {
        private const val TAG = "AapConnection"
    }

    private val _state = MutableStateFlow(AapPodState())
    val state: StateFlow<AapPodState> = _state.asStateFlow()

    private var socket: BluetoothSocket? = null
    private var readerJob: Job? = null
    private val writeMutex = Mutex()
    private val framer = AapFramer()

    suspend fun connect() = withContext(Dispatchers.IO) {
        if (_state.value.connectionState != AapConnectionState.DISCONNECTED) {
            Log.w(TAG, "connect() called in state ${_state.value.connectionState}")
            return@withContext
        }

        _state.value = _state.value.copy(connectionState = AapConnectionState.CONNECTING)

        try {
            val sock = socketFactory.createSocket(device, psm)
            sock.connect()
            socket = sock
            Log.d(TAG, "Connected to ${device.address}")

            _state.value = _state.value.copy(connectionState = AapConnectionState.HANDSHAKING)

            // Send handshake
            val handshake = profile.encodeHandshake()
            sock.outputStream.write(handshake)
            sock.outputStream.flush()
            Log.d(TAG, "Handshake sent")

            // Start read loop
            coroutineScope {
                readerJob = launch { readLoop(sock) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed", e)
            cleanupSocket()
            _state.value = AapPodState(connectionState = AapConnectionState.DISCONNECTED)
            throw e
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        Log.d(TAG, "Disconnecting")
        readerJob?.cancel()
        readerJob = null
        cleanupSocket()
        framer.reset()
        _state.value = AapPodState(connectionState = AapConnectionState.DISCONNECTED)
    }

    suspend fun send(command: AapCommand) {
        val currentState = _state.value
        if (currentState.connectionState != AapConnectionState.READY) {
            throw IllegalStateException("Cannot send command in state ${currentState.connectionState}")
        }

        val bytes = profile.encodeCommand(command)
        writeMutex.withLock {
            withContext(Dispatchers.IO) {
                val sock = socket ?: throw IOException("Socket is null")
                sock.outputStream.write(bytes)
                sock.outputStream.flush()
                Log.d(TAG, "Sent command: $command (${bytes.size} bytes)")
            }
        }
    }

    private suspend fun readLoop(sock: BluetoothSocket) = withContext(Dispatchers.IO) {
        val buf = ByteArray(2048)
        var handshakeResponseReceived = false

        try {
            while (isActive) {
                val len = sock.inputStream.read(buf)
                if (len == -1) {
                    Log.d(TAG, "Stream closed by remote")
                    break
                }

                val messages = framer.consume(buf, 0, len)
                for (message in messages) {
                    processMessage(message)

                    if (!handshakeResponseReceived && message.commandType != 0x0009) {
                        handshakeResponseReceived = true
                    }
                }

                // Transition to READY after processing first batch of messages
                if (handshakeResponseReceived && _state.value.connectionState == AapConnectionState.HANDSHAKING) {
                    _state.value = _state.value.copy(connectionState = AapConnectionState.READY)
                    Log.d(TAG, "Connection READY")
                }
            }
        } catch (e: IOException) {
            if (isActive) Log.e(TAG, "Read error", e)
        } finally {
            cleanupSocket()
            _state.value = _state.value.copy(connectionState = AapConnectionState.DISCONNECTED)
        }
    }

    private fun processMessage(message: AapMessage) {
        // Try device info
        profile.decodeDeviceInfo(message)?.let { info ->
            _state.value = _state.value.copy(deviceInfo = info)
            Log.d(TAG, "Device info: ${info.name} (${info.modelNumber})")
            return
        }

        // Try setting update (merge into existing state)
        profile.decodeSetting(message)?.let { (key, value) ->
            _state.value = _state.value.withSetting(key, value)
        }
    }

    private fun cleanupSocket() {
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        socket = null
    }
}
