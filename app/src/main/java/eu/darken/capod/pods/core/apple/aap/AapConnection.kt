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
import kotlin.reflect.KClass
import eu.darken.capod.pods.core.apple.aap.protocol.AapCommand
import eu.darken.capod.pods.core.apple.aap.protocol.AapDeviceProfile
import eu.darken.capod.pods.core.apple.aap.protocol.AapFramer
import eu.darken.capod.pods.core.apple.aap.protocol.AapMessage
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import eu.darken.capod.pods.core.apple.aap.protocol.KeyExchangeResult
import eu.darken.capod.pods.core.apple.aap.protocol.StemPressEvent
import kotlinx.coroutines.channels.BufferOverflow
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * Manages a single AAP L2CAP connection to a device.
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

    private val _state = MutableStateFlow(AapPodState())
    val state: StateFlow<AapPodState> = _state.asStateFlow()

    private val _keysReceived = MutableSharedFlow<KeyExchangeResult>(extraBufferCapacity = 1)
    val keysReceived: SharedFlow<KeyExchangeResult> = _keysReceived.asSharedFlow()

    private val _stemPressEvents = MutableSharedFlow<StemPressEvent>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val stemPressEvents: SharedFlow<StemPressEvent> = _stemPressEvents.asSharedFlow()

    private var socket: BluetoothSocket? = null
    private var readerJob: Job? = null
    private val writeMutex = Mutex()
    private val sendMutex = Mutex()
    private val framer = AapFramer()

    private var pendingAncMode: AapSetting.AncMode.Value? = null
    private var ancDebounceJob: Job? = null
    private var connectionScope: CoroutineScope? = null
    private var lastAncCommandSentAt: Long = 0L
    private var lastCommandedAncMode: AapSetting.AncMode.Value? = null
    private var ancResendJob: Job? = null

    /**
     * Opens the L2CAP socket, sends the handshake, and launches the read loop.
     * Returns after the handshake is sent — the read loop runs in [scope] independently.
     */
    suspend fun connect(scope: CoroutineScope) = withContext(Dispatchers.IO) {
        if (_state.value.connectionState != AapPodState.ConnectionState.DISCONNECTED) {
            throw IllegalStateException("connect() called in state ${_state.value.connectionState}")
        }

        connectionScope = scope
        _state.value = _state.value.copy(connectionState = AapPodState.ConnectionState.CONNECTING)

        try {
            val sock = socketFactory.createSocket(device, psm)
            sock.connect()
            socket = sock
            log(TAG) { "Connected to ${device.address}" }

            _state.value = _state.value.copy(connectionState = AapPodState.ConnectionState.HANDSHAKING)

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

            // Send InitExt for models that need it (Pro 2/3/USB-C, AP4 ANC)
            profile.encodeInitExt()?.let { initExt ->
                sock.outputStream.write(initExt)
                sock.outputStream.flush()
                log(TAG) { "InitExt sent" }
            }

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
            _state.value = AapPodState(connectionState = AapPodState.ConnectionState.DISCONNECTED)
            throw e
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        log(TAG) { "Disconnecting" }
        readerJob?.cancel()
        readerJob = null
        ancDebounceJob?.cancel()
        ancDebounceJob = null
        ancResendJob?.cancel()
        ancResendJob = null
        pendingAncMode = null
        lastCommandedAncMode = null
        connectionScope = null
        cleanupSocket()
        framer.reset()
        _state.value = AapPodState(connectionState = AapPodState.ConnectionState.DISCONNECTED)
    }

    suspend fun send(command: AapCommand) = sendMutex.withLock {
        val currentState = _state.value
        if (currentState.connectionState != AapPodState.ConnectionState.READY) {
            throw IllegalStateException("Cannot send command in state ${currentState.connectionState}")
        }

        if (command is AapCommand.SetAncMode) {
            lastCommandedAncMode = command.mode
            ancResendJob?.cancel()
            ancResendJob = null
            val earDetection = currentState.setting<AapSetting.EarDetection>()
            if (earDetection != null && !earDetection.isEitherPodInEar) {
                log(TAG) { "No pod in ear, queuing ANC mode: ${command.mode}" }
                pendingAncMode = command.mode
                _state.value = currentState.copy(pendingAncMode = command.mode)
                return@withLock
            }
            pendingAncMode = null
            // Optimistically update UI — don't wait for device echo
            val currentAnc = currentState.setting<AapSetting.AncMode>()
            if (currentAnc != null) {
                _state.value = currentState
                    .withSetting(AapSetting.AncMode::class, currentAnc.copy(current = command.mode))
                    .copy(pendingAncMode = null, lastMessageAt = timeSource.now())
            } else {
                _state.value = currentState.copy(pendingAncMode = null)
            }
        }

        val preSendDeviceInfo = currentState.deviceInfo
        applyOptimisticUpdate(currentState, command)
        try {
            sendRaw(command)
        } catch (e: Exception) {
            // Rollback is scoped to rename — the name is the one user-visible writable field
            // that has no device echo to correct an incorrect optimistic update, so a failed
            // send would otherwise leave the UI permanently lying about the device name.
            if (command is AapCommand.SetDeviceName && preSendDeviceInfo != null) {
                _state.value = _state.value.copy(deviceInfo = preSendDeviceInfo)
            }
            throw e
        }
    }

    /**
     * Optimistically update state for non-ANC settings so the UI reflects the change immediately.
     * ANC is handled separately in [send] due to ear-detection gating.
     * If the setting hasn't been reported by the device yet, skip (UI wouldn't show the toggle).
     */
    private fun applyOptimisticUpdate(baseState: AapPodState, command: AapCommand) {
        val updated: Pair<KClass<out AapSetting>, AapSetting> = when (command) {
            is AapCommand.SetAncMode -> return // Handled in send()
            is AapCommand.SetConversationalAwareness -> {
                val cur = baseState.setting<AapSetting.ConversationalAwareness>() ?: return
                AapSetting.ConversationalAwareness::class to cur.copy(enabled = command.enabled)
            }
            is AapCommand.SetNcWithOneAirPod -> {
                val cur = baseState.setting<AapSetting.NcWithOneAirPod>() ?: return
                AapSetting.NcWithOneAirPod::class to cur.copy(enabled = command.enabled)
            }
            is AapCommand.SetVolumeSwipe -> {
                val cur = baseState.setting<AapSetting.VolumeSwipe>() ?: return
                AapSetting.VolumeSwipe::class to cur.copy(enabled = command.enabled)
            }
            is AapCommand.SetPersonalizedVolume -> {
                val cur = baseState.setting<AapSetting.PersonalizedVolume>() ?: return
                AapSetting.PersonalizedVolume::class to cur.copy(enabled = command.enabled)
            }
            is AapCommand.SetToneVolume -> {
                baseState.setting<AapSetting.ToneVolume>() ?: return
                AapSetting.ToneVolume::class to AapSetting.ToneVolume(level = command.level)
            }
            is AapCommand.SetAdaptiveAudioNoise -> {
                baseState.setting<AapSetting.AdaptiveAudioNoise>() ?: return
                AapSetting.AdaptiveAudioNoise::class to AapSetting.AdaptiveAudioNoise(level = command.level)
            }
            is AapCommand.SetPressSpeed -> {
                baseState.setting<AapSetting.PressSpeed>() ?: return
                AapSetting.PressSpeed::class to AapSetting.PressSpeed(value = command.value)
            }
            is AapCommand.SetPressHoldDuration -> {
                baseState.setting<AapSetting.PressHoldDuration>() ?: return
                AapSetting.PressHoldDuration::class to AapSetting.PressHoldDuration(value = command.value)
            }
            is AapCommand.SetVolumeSwipeLength -> {
                baseState.setting<AapSetting.VolumeSwipeLength>() ?: return
                AapSetting.VolumeSwipeLength::class to AapSetting.VolumeSwipeLength(value = command.value)
            }
            is AapCommand.SetEndCallMuteMic -> {
                baseState.setting<AapSetting.EndCallMuteMic>() ?: return
                AapSetting.EndCallMuteMic::class to AapSetting.EndCallMuteMic(muteMic = command.muteMic, endCall = command.endCall)
            }
            is AapCommand.SetMicrophoneMode -> {
                AapSetting.MicrophoneMode::class to AapSetting.MicrophoneMode(mode = command.mode)
            }
            is AapCommand.SetEarDetectionEnabled -> {
                AapSetting.EarDetectionEnabled::class to AapSetting.EarDetectionEnabled(enabled = command.enabled)
            }
            is AapCommand.SetListeningModeCycle -> {
                AapSetting.ListeningModeCycle::class to AapSetting.ListeningModeCycle(modeMask = command.modeMask)
            }
            is AapCommand.SetAllowOffOption -> {
                AapSetting.AllowOffOption::class to AapSetting.AllowOffOption(enabled = command.enabled)
            }
            is AapCommand.SetStemConfig -> {
                AapSetting.StemConfig::class to AapSetting.StemConfig(claimedPressMask = command.claimedPressMask)
            }
            is AapCommand.SetSleepDetection -> {
                AapSetting.SleepDetection::class to AapSetting.SleepDetection(enabled = command.enabled)
            }
            is AapCommand.SetDeviceName -> {
                val currentInfo = baseState.deviceInfo ?: return
                _state.value = baseState
                    .copy(deviceInfo = currentInfo.copy(name = command.name), lastMessageAt = timeSource.now())
                return
            }
        }
        _state.value = baseState.withSetting(updated.first, updated.second).copy(lastMessageAt = timeSource.now())
    }

    private suspend fun sendRaw(command: AapCommand) {
        val bytes = profile.encodeCommand(command)
        writeMutex.withLock {
            withContext(Dispatchers.IO) {
                val sock = socket ?: throw IOException("Socket is null")
                sock.outputStream.write(bytes)
                sock.outputStream.flush()
                if (command is AapCommand.SetAncMode) lastAncCommandSentAt = timeSource.currentTimeMillis()
                log(TAG) { "Sent command: $command (${bytes.size} bytes)" }
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
                    log(TAG) { "Stream closed by remote" }
                    break
                }

                // L2CAP SEQPACKET: each read() returns exactly one complete message
                val raw = buf.copyOfRange(0, len)
                val message = AapMessage.Companion.parse(raw)
                if (message != null) {
                    processMessage(message)
                    if (!handshakeResponseReceived && message.commandType != 0x0009) {
                        handshakeResponseReceived = true
                    }
                }

                // Transition to READY after processing first batch of messages
                if (handshakeResponseReceived && _state.value.connectionState == AapPodState.ConnectionState.HANDSHAKING) {
                    _state.value = _state.value.copy(connectionState = AapPodState.ConnectionState.READY)
                    log(TAG) { "Connection READY" }
                }
            }
        } catch (e: IOException) {
            if (isActive) log(TAG, ERROR) { "Read error: $e" }
        } finally {
            ancDebounceJob?.cancel()
            ancResendJob?.cancel()
            pendingAncMode = null
            lastCommandedAncMode = null
            cleanupSocket()
            _state.value = _state.value.copy(
                connectionState = AapPodState.ConnectionState.DISCONNECTED,
                pendingAncMode = null,
            )
        }
    }

    private fun processMessage(message: AapMessage) {
        val hex = message.raw.joinToString(" ") { "%02X".format(it) }
        log(TAG, VERBOSE) { "MSG cmd=0x${"%04X".format(message.commandType)} len=${message.raw.size} raw=$hex" }

        // Issue #173: diagnostic dump of the 0x1D INFORMATION packet so testers with engraved AirPods
        // can share a debug recording that reveals where (or whether) the engraving message lives.
        // Runs unconditionally for 0x1D — not gated on decodeDeviceInfo success, since an engraving-
        // shaped packet may not match the strict production decoder's expectations.
        if (message.commandType == 0x001D) {
            val segments = describeDeviceInfoSegments(message.payload)
            log(TAG, INFO) { "DeviceInfoDump #173: payload=${message.payload.size} bytes, segments=${segments.size}" }
            segments.forEach { seg ->
                val label = when (seg.index) {
                    0 -> "name"
                    1 -> "modelNumber"
                    2 -> "manufacturer"
                    3 -> "serialNumber"
                    4 -> "firmwareVersion"
                    else -> "unknown"
                }
                val rendered = seg.utf8?.let { "\"$it\"" } ?: "<non-utf8>"
                log(TAG, INFO) { "DeviceInfoDump #173: [${seg.index}] off=${seg.offset} len=${seg.length} ($label) $rendered hex=${seg.hex}" }
            }
        }

        // Try stem press event (transient — emitted via SharedFlow, not stored in state)
        profile.decodeStemPress(message)?.let { event ->
            _stemPressEvents.tryEmit(event)
            log(TAG) { "Stem press: ${event.pressType} ${event.bud}" }
            return
        }

        // Try battery
        profile.decodeBattery(message)?.let { batteries ->
            // Filter DISCONNECTED entries (e.g. case reports 0% DISCONNECTED when pods are removed).
            // Merge with existing state so previously-known values are preserved for absent slots.
            val valid = batteries.filterValues { it.charging != AapPodState.ChargingState.DISCONNECTED }
            _state.value = _state.value.copy(
                batteries = _state.value.batteries + valid,
                lastMessageAt = timeSource.now(),
            )
            log(TAG) { "Battery update: ${batteries.entries.map { "${it.key}=${(it.value.percent * 100).toInt()}% ${it.value.charging}" }}" }
            return
        }

        // Try private key response
        profile.decodePrivateKeyResponse(message)?.let { keys ->
            log(TAG) { "Private keys received: IRK=${keys.irk != null}, ENC=${keys.encKey != null}" }
            _state.value = _state.value.copy(lastMessageAt = timeSource.now())
            _keysReceived.tryEmit(keys)
            return
        }

        // Try device info
        profile.decodeDeviceInfo(message)?.let { info ->
            _state.value = _state.value.copy(deviceInfo = info, lastMessageAt = timeSource.now())
            log(TAG) { "Device info: ${info.name} (${info.modelNumber})" }
            return
        }

        // Try setting update (merge into existing state)
        profile.decodeSetting(message)?.let { (key, value) ->
            // Debounce device-initiated ANC mode changes (firmware cycles modes on ear transitions).
            // Skip debounce for: first ANC mode (initial setup), echoes after our own command.
            if (value is AapSetting.AncMode) {
                val isFirstAncMode = _state.value.setting<AapSetting.AncMode>() == null
                val sinceLastCommand = timeSource.currentTimeMillis() - lastAncCommandSentAt
                if (isFirstAncMode || sinceLastCommand <= 3000L) {
                    ancDebounceJob?.cancel()
                    _state.value = _state.value.withSetting(key, value).copy(lastMessageAt = timeSource.now())
                    log(TAG) { "Setting: ${key.simpleName} = $value" }

                    // After our command, firmware may cycle through modes before settling.
                    // Schedule a verification: if settled mode != commanded mode, re-send once.
                    lastCommandedAncMode?.let { commanded ->
                        ancResendJob?.cancel()
                        ancResendJob = connectionScope?.launch {
                            delay(1000L)
                            val current = _state.value.setting<AapSetting.AncMode>()?.current
                            if (current != null && current != commanded) {
                                log(TAG) { "ANC mode diverged: commanded=$commanded settled=$current, re-sending" }
                                lastCommandedAncMode = null
                                sendRaw(AapCommand.SetAncMode(commanded))
                            } else {
                                lastCommandedAncMode = null
                            }
                        }
                    }
                } else {
                    ancDebounceJob?.cancel()
                    ancDebounceJob = connectionScope?.launch {
                        delay(1500L)
                        _state.value = _state.value.withSetting(key, value).copy(lastMessageAt = timeSource.now())
                        log(TAG) { "Setting (debounced): ${key.simpleName} = $value" }
                    }
                }
                return
            }

            // Detect ear detection role swap — clear stale PrimaryPod until 0x0008 refreshes it
            val clearPrimaryPod = value is AapSetting.EarDetection && run {
                val prev = _state.value.setting<AapSetting.EarDetection>()
                prev != null && prev.primaryPod == value.secondaryPod && prev.secondaryPod == value.primaryPod
            }

            var newState = _state.value.withSetting(key, value).copy(lastMessageAt = timeSource.now())
            if (clearPrimaryPod) {
                newState = newState.copy(settings = newState.settings - AapSetting.PrimaryPod::class)
            }
            _state.value = newState
            log(TAG) { "Setting: ${key.simpleName} = $value${if (clearPrimaryPod) " (swap, PrimaryPod cleared)" else ""}" }

            // Flush queued ANC command when a pod goes in ear
            if (value is AapSetting.EarDetection && value.isEitherPodInEar) {
                pendingAncMode?.let { mode ->
                    pendingAncMode = null
                    // Optimistic update — show target mode immediately, don't wait for device echo
                    val currentAnc = _state.value.setting<AapSetting.AncMode>()
                    if (currentAnc != null) {
                        _state.value = _state.value
                            .withSetting(AapSetting.AncMode::class, currentAnc.copy(current = mode))
                            .copy(pendingAncMode = null, lastMessageAt = timeSource.now())
                    } else {
                        _state.value = _state.value.copy(pendingAncMode = null)
                    }
                    log(TAG) { "Pod in ear, sending queued ANC mode: $mode" }
                    connectionScope?.launch { sendRaw(AapCommand.SetAncMode(mode)) }
                }
            }
            return
        }

        log(TAG) { "Unhandled message: cmd=0x${"%04X".format(message.commandType)} payload=${message.payload.size}B" }
    }

    private fun cleanupSocket() {
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        socket = null
    }

    companion object {
        private val TAG = logTag("AapConnection")

        /**
         * Diagnostic-only NUL-delimited segmentation of a 0x1D INFORMATION payload, used for the
         * issue #173 engraving discovery logging. Not part of the production decode path — the
         * production parser [eu.darken.capod.pods.core.apple.aap.protocol.DefaultAapDeviceProfile]
         * intentionally stays ASCII-only to preserve existing wire-format assumptions.
         *
         * Skips binary header bytes until the first printable ASCII byte (mirroring the production
         * decoder's start condition — all known captures begin the real data with the device name,
         * which is always ASCII like "AirPods Pro"), then splits on NUL bytes. Each non-empty chunk
         * becomes one [DeviceInfoSegment]. UTF-8 decode is attempted strictly; if a chunk contains
         * invalid UTF-8 (e.g. the post-serials encrypted blob), [DeviceInfoSegment.utf8] is null
         * and the caller falls back to [DeviceInfoSegment.hex].
         *
         * Offsets are relative to the supplied payload (post message-header), matching what the
         * caller holds in [AapMessage.payload].
         */
        internal fun describeDeviceInfoSegments(payload: ByteArray): List<DeviceInfoSegment> {
            var start = 0
            while (start < payload.size) {
                val b = payload[start].toInt() and 0xFF
                if (b in 0x20..0x7E) break
                start++
            }
            if (start >= payload.size) return emptyList()

            val segments = mutableListOf<DeviceInfoSegment>()
            var segIndex = 0
            var i = start
            while (i < payload.size) {
                while (i < payload.size && payload[i] == 0x00.toByte()) i++
                if (i >= payload.size) break

                val segStart = i
                while (i < payload.size && payload[i] != 0x00.toByte()) i++
                val segBytes = payload.copyOfRange(segStart, i)

                val utf8: String? = try {
                    Charsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(segBytes))
                        .toString()
                } catch (_: CharacterCodingException) {
                    null
                }
                val hex = segBytes.joinToString("") { "%02X".format(it) }

                segments.add(
                    DeviceInfoSegment(
                        index = segIndex,
                        offset = segStart,
                        length = segBytes.size,
                        utf8 = utf8,
                        hex = hex,
                    )
                )
                segIndex++
            }
            return segments
        }

        internal data class DeviceInfoSegment(
            val index: Int,
            val offset: Int,
            val length: Int,
            val utf8: String?,
            val hex: String,
        )
    }
}
