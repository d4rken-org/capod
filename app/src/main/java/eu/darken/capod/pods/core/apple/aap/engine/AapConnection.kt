package eu.darken.capod.pods.core.apple.aap.engine

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import eu.darken.capod.common.TimeSource
import eu.darken.capod.common.bluetooth.l2cap.L2capSocketFactory
import eu.darken.capod.common.debug.logging.Logging
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.pods.core.apple.aap.AapPodState
import eu.darken.capod.pods.core.apple.aap.protocol.AapCommand
import eu.darken.capod.pods.core.apple.aap.protocol.AapDeviceProfile
import eu.darken.capod.pods.core.apple.aap.protocol.AapFramer
import eu.darken.capod.pods.core.apple.aap.protocol.AapPacket
import eu.darken.capod.pods.core.apple.aap.protocol.AapSleepEvent
import eu.darken.capod.pods.core.apple.aap.protocol.KeyExchangeResult
import eu.darken.capod.pods.core.apple.aap.protocol.StemPressEvent
import kotlinx.coroutines.CompletableDeferred
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
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Manages a single AAP L2CAP connection to a device.
 * Thin socket wrapper — all session logic lives in [AapSessionEngine].
 * Internal — not exposed outside [eu.darken.capod.pods.core.apple.aap.AapConnectionManager].
 */
@SuppressLint("MissingPermission")
internal class AapConnection(
    private val device: BluetoothDevice,
    private val profile: AapDeviceProfile,
    private val socketFactory: L2capSocketFactory,
    timeSource: TimeSource,
    private val connectTimeout: Duration = DEFAULT_CONNECT_TIMEOUT,
) {

    private val engine = AapSessionEngine(profile, timeSource)

    val state: StateFlow<AapPodState> get() = engine.state
    val keysReceived: SharedFlow<KeyExchangeResult> get() = engine.keysReceived
    val stemPressEvents: SharedFlow<StemPressEvent> get() = engine.stemPressEvents
    val sleepEvents: SharedFlow<AapSleepEvent> get() = engine.sleepEvents
    val offRejected: SharedFlow<Unit> get() = engine.offRejected
    val settingRejected: SharedFlow<AapCommand> get() = engine.settingRejected

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
            val sock = socketFactory.createSocket(device, PSM)
            sock.connectCancellable()
            socket = sock
            log(TAG, Logging.Priority.INFO) { "Connected to ${device.address}" }

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
            log(TAG, Logging.Priority.ERROR) { "Connection failed: $e" }
            cleanupSocket()
            engine.reset()
            throw e
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        log(TAG, Logging.Priority.INFO) { "Disconnecting" }
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
                log(TAG, Logging.Priority.VERBOSE) { "SEND cmd=$command len=${bytes.size} raw=$hex" }
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

                // L2CAP SEQPACKET: each read() returns exactly one complete frame
                val raw = buf.copyOfRange(0, len)
                val packet = AapPacket.parse(raw) ?: continue

                when (packet) {
                    is AapPacket.Message -> engine.processMessage(packet)
                    is AapPacket.ConnectResponse -> {
                        engine.processConnectResponse(packet)
                        if (packet.status == 0) dispatchCaseInfoProbe()
                    }
                    is AapPacket.Disconnect -> {
                        log(TAG, Logging.Priority.INFO) {
                            "Disconnect received: service=0x${"%04X".format(packet.service)} status=0x${"%04X".format(packet.status)}"
                        }
                    }
                    is AapPacket.DisconnectResponse -> {
                        log(TAG) { "DisconnectResponse received: service=0x${"%04X".format(packet.service)}" }
                    }
                    is AapPacket.Connect -> {
                        // Unexpected — we're the source, not the peer. Log and ignore.
                        log(TAG, Logging.Priority.WARN) {
                            "Unexpected Connect packet from peer: service=0x${"%04X".format(packet.service)}"
                        }
                    }
                    is AapPacket.Unknown -> {
                        val hex = packet.raw.joinToString(" ") { "%02X".format(it) }
                        log(TAG, Logging.Priority.INFO) {
                            "Unknown AAP packet type=0x${"%04X".format(packet.packetType)} len=${packet.raw.size} raw=$hex"
                        }
                    }
                }
            }
        } catch (e: IOException) {
            if (isActive) log(TAG, Logging.Priority.ERROR) { "Read error: $e" }
        } finally {
            engine.reset()
            cleanupSocket()
        }
    }

    /**
     * Fire-and-forget Case Info request (message type 0x22). Sent after a
     * successful Connect Response. Not an AapCommand — bypasses the outbound
     * queue, ear-gating, and pending-settings counter. If the device doesn't
     * reply, nothing happens; the next connect attempt re-probes.
     */
    private suspend fun dispatchCaseInfoProbe() {
        val bytes = profile.encodeCaseInfoRequest() ?: return
        try {
            writeMutex.withLock {
                withContext(Dispatchers.IO) {
                    val sock = socket ?: return@withContext
                    sock.outputStream.write(bytes)
                    sock.outputStream.flush()
                    val hex = bytes.joinToString(" ") { "%02X".format(it) }
                    log(TAG, Logging.Priority.VERBOSE) { "SEND CaseInfoProbe len=${bytes.size} raw=$hex" }
                }
            }
        } catch (e: Exception) {
            // Non-critical: the probe is fire-and-forget; a send failure just means
            // we don't get Case Info this session. No retry, no state change.
            log(TAG, Logging.Priority.WARN) { "CaseInfoProbe send failed: $e" }
        }
    }

    private fun cleanupSocket() {
        socket?.closeQuietly()
        socket = null
    }

    private suspend fun BluetoothSocket.connectCancellable() {
        val result = CompletableDeferred<Result<Unit>>()
        val cancelled = AtomicBoolean(false)
        val connectThread = Thread(
            {
                val outcome = runCatching { connect() }
                result.complete(outcome)
                if (cancelled.get() && outcome.isSuccess) closeQuietly()
            },
            "AAP-L2CAP-connect-${device.address}",
        ).apply {
            isDaemon = true
            start()
        }

        try {
            withTimeout(connectTimeout) {
                result.await().getOrThrow()
            }
        } catch (e: Exception) {
            cancelled.set(true)
            closeQuietly()
            connectThread.interrupt()
            throw e
        }
    }

    private fun BluetoothSocket.closeQuietly() {
        try {
            close()
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val PSM = 0x1001
        internal val DEFAULT_CONNECT_TIMEOUT = 5.seconds
        private val TAG = logTag("AAP", "Connection")
    }
}
