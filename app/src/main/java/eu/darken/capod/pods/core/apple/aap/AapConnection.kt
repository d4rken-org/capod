package eu.darken.capod.pods.core.apple.aap

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import eu.darken.capod.common.TimeSource
import eu.darken.capod.common.bluetooth.l2cap.L2capSocketFactory
import eu.darken.capod.common.debug.logging.Logging.Priority.ERROR
import eu.darken.capod.common.debug.logging.Logging.Priority.INFO
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.pods.core.apple.aap.protocol.AapCommand
import eu.darken.capod.pods.core.apple.aap.protocol.AapDeviceProfile
import eu.darken.capod.pods.core.apple.aap.protocol.AapFramer
import eu.darken.capod.pods.core.apple.aap.protocol.AapMessage
import eu.darken.capod.pods.core.apple.aap.protocol.KeyExchangeResult
import eu.darken.capod.pods.core.apple.aap.protocol.StemPressEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Manages a single AAP L2CAP connection to a device.
 * Thin socket wrapper — all session logic lives in [AapSessionEngine].
 * Internal — not exposed outside [AapConnectionManager].
 */
@SuppressLint("MissingPermission")
internal class AapConnection(
    private val device: BluetoothDevice,
    private val profile: AapDeviceProfile,
    private val socketFactory: L2capSocketFactory,
    private val timeSource: TimeSource,
    private val psm: Int = 0x1001,
) {

    private val engine = AapSessionEngine(profile, timeSource)

    val state: StateFlow<AapPodState> get() = engine.state
    val keysReceived: SharedFlow<KeyExchangeResult> get() = engine.keysReceived
    val stemPressEvents: SharedFlow<StemPressEvent> get() = engine.stemPressEvents

    private var socket: BluetoothSocket? = null
    private var readerJob: Job? = null
    private val writeMutex = Mutex()
    private val framer = AapFramer()

    /**
     * Opens the L2CAP socket, sends the handshake, and launches the read loop.
     * Returns after the handshake is sent — the read loop runs in [scope] independently.
     */
    suspend fun connect(scope: CoroutineScope) = withContext(Dispatchers.IO) {
        if (state.value.connectionState != AapPodState.ConnectionState.DISCONNECTED) {
            throw IllegalStateException("connect() called in state ${state.value.connectionState}")
        }

        engine.start(scope)

        try {
            val sock = socketFactory.createSocket(device, psm)
            sock.connect()
            socket = sock
            log(TAG, INFO) { "Connected to ${device.address}" }

            engine.onHandshakeSent()

            // Send handshake
            val handshake = profile.encodeHandshake()
            sock.outputStream.write(handshake)
            sock.outputStream.flush()
            log(TAG) { "Handshake sent" }

            // Send notification enable packets — tells device to push battery/settings updates
            for (packet in profile.encodeNotificationEnable()) {
                sock.outputStream.write(packet)
                sock.outputStream.flush()
            }
            log(TAG) { "Notification enable sent" }

            // Send InitExt — enables advanced features on H2+ devices, ignored by older models
            sock.outputStream.write(profile.encodeInitExt())
            sock.outputStream.flush()
            log(TAG) { "InitExt sent" }

            // Request private keys (IRK + ENC) for BLE encrypted battery
            profile.encodePrivateKeyRequest()?.let { keyReq ->
                sock.outputStream.write(keyReq)
                sock.outputStream.flush()
                log(TAG) { "Private key request sent" }
            }

            // Launch read loop in the provided scope — connect() returns immediately
            readerJob = scope.launch(Dispatchers.IO) { readLoop(sock) }
        } catch (e: Exception) {
            log(TAG, ERROR) { "Connection failed: $e" }
            cleanupSocket()
            engine.reset()
            throw e
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        log(TAG, INFO) { "Disconnecting" }
        readerJob?.cancel()
        readerJob = null
        engine.reset()
        cleanupSocket()
        framer.reset()
    }

    suspend fun send(command: AapCommand) {
        engine.send(command, ::sendRaw)
    }

    private suspend fun sendRaw(command: AapCommand) {
        val bytes = profile.encodeCommand(command)
        writeMutex.withLock {
            withContext(Dispatchers.IO) {
                val sock = socket ?: throw IOException("Socket is null")
                sock.outputStream.write(bytes)
                sock.outputStream.flush()
                val hex = bytes.joinToString(" ") { "%02X".format(it) }
                log(TAG, VERBOSE) { "SEND cmd=$command len=${bytes.size} raw=$hex" }
            }
        }
    }

    private suspend fun readLoop(sock: BluetoothSocket) = withContext(Dispatchers.IO) {
        val buf = ByteArray(2048)

        try {
            while (isActive) {
                val len = sock.inputStream.read(buf)
                if (len == -1) {
                    log(TAG) { "Stream closed by remote" }
                    break
                }

                // L2CAP SEQPACKET: each read() returns exactly one complete message
                val raw = buf.copyOfRange(0, len)
                val message = AapMessage.Companion.parse(raw)
                if (message != null) {
                    engine.processMessage(message)
                }
            }
        } catch (e: IOException) {
            if (isActive) log(TAG, ERROR) { "Read error: $e" }
        } finally {
            engine.reset()
            cleanupSocket()
        }
    }

    private fun cleanupSocket() {
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        socket = null
    }

    companion object {
        private val TAG = logTag("AAP", "Connection")
    }
}
