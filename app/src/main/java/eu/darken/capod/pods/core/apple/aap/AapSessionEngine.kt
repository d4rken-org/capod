package eu.darken.capod.pods.core.apple.aap

import eu.darken.capod.common.TimeSource
import eu.darken.capod.common.debug.logging.Logging.Priority.ERROR
import eu.darken.capod.common.debug.logging.Logging.Priority.INFO
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.pods.core.apple.aap.protocol.AapCommand
import eu.darken.capod.pods.core.apple.aap.protocol.AapDeviceProfile
import eu.darken.capod.pods.core.apple.aap.protocol.AapMessage
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import eu.darken.capod.pods.core.apple.aap.protocol.KeyExchangeResult
import eu.darken.capod.pods.core.apple.aap.protocol.StemPressEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.reflect.KClass

/**
 * Owns all AAP session state and decision-making. [AapConnection] is a thin socket wrapper
 * that delegates to this engine for send-path logic and incoming message processing.
 */
internal class AapSessionEngine(
    private val profile: AapDeviceProfile,
    private val timeSource: TimeSource,
) {

    // ── State ───────────────────────────────────────────────

    private val _state = MutableStateFlow(AapPodState())
    val state: StateFlow<AapPodState> = _state.asStateFlow()

    private val _keysReceived = MutableSharedFlow<KeyExchangeResult>(extraBufferCapacity = 1)
    val keysReceived: SharedFlow<KeyExchangeResult> = _keysReceived.asSharedFlow()

    private val _stemPressEvents =
        MutableSharedFlow<StemPressEvent>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val stemPressEvents: SharedFlow<StemPressEvent> = _stemPressEvents.asSharedFlow()

    private val coordinator = AapSettingsCoordinator(timeSource)
    private val sendMutex = Mutex()

    private var scope: CoroutineScope? = null
    private var ancDebounceJob: Job? = null
    private var lastSentCommand: AapCommand? = null
    private var lastSentAt: Long = 0L
    /** Separate tracking for ANC sends — not overwritten by non-ANC commands during flush. */
    private var lastAncSentAt: Long = 0L
    private var handshakeResponseReceived: Boolean = false

    /** Stored reference to the socket write callback — set on each [send] / flush call. */
    private var activeSendRaw: (suspend (AapCommand) -> Unit)? = null

    // ── Lifecycle ───────────────────────────────────────────

    /** Begin a session: set scope, transition to CONNECTING. */
    fun start(scope: CoroutineScope) {
        this.scope = scope
        _state.value = _state.value.copy(connectionState = AapPodState.ConnectionState.CONNECTING)
    }

    /** Called after handshake bytes are sent on the socket. */
    fun onHandshakeSent() {
        _state.value = _state.value.copy(connectionState = AapPodState.ConnectionState.HANDSHAKING)
        handshakeResponseReceived = false
    }

    /** Idempotent reset: cancel all jobs, clear queue, reset state to DISCONNECTED. */
    fun reset() {
        ancDebounceJob?.cancel()
        ancDebounceJob = null
        coordinator.clear()
        scope = null
        lastSentCommand = null
        lastSentAt = 0L
        lastAncSentAt = 0L
        activeSendRaw = null
        handshakeResponseReceived = false
        _state.value = AapPodState(connectionState = AapPodState.ConnectionState.DISCONNECTED)
    }

    // ── Send path ───────────────────────────────────────────

    suspend fun send(command: AapCommand, sendRaw: suspend (AapCommand) -> Unit) = sendMutex.withLock {
        activeSendRaw = sendRaw
        val currentState = _state.value
        if (currentState.connectionState != AapPodState.ConnectionState.READY) {
            throw IllegalStateException("Cannot send command in state ${currentState.connectionState}")
        }

        // Ear-detection gating — defer all setting commands except SetDeviceName
        if (command !is AapCommand.SetDeviceName) {
            val earDetection = currentState.setting<AapSetting.EarDetection>()
            if (earDetection != null && !earDetection.isEitherPodInEar) {
                log(TAG) { "No pod in ear, queuing: ${command::class.simpleName}" }
                val (optimistic, snapshot) = coordinator.enqueue(command, currentState)
                optimistic?.let { _state.value = it }
                _state.value = _state.value.copy(
                    pendingAncMode = snapshot.pendingAncMode,
                    pendingSettingsCount = snapshot.count,
                )
                return@withLock
            }
        }

        // Immediate send path (pods in ear, or SetDeviceName)
        if (command is AapCommand.SetAncMode) {
            val snapshot = coordinator.removeFromQueue(AapCommand.SetAncMode::class)
            val currentAnc = currentState.setting<AapSetting.AncMode>()
            if (currentAnc != null) {
                _state.value =
                    currentState.withSetting(AapSetting.AncMode::class, currentAnc.copy(current = command.mode))
                        .copy(lastMessageAt = timeSource.now())
            }
            _state.value = _state.value.copy(
                pendingAncMode = snapshot.pendingAncMode,
                pendingSettingsCount = snapshot.count,
            )
        }

        val preSendDeviceInfo = currentState.deviceInfo
        coordinator.optimisticUpdate(currentState, command)?.let { _state.value = it }
        try {
            wrappedSend(sendRaw, command)
        } catch (e: Exception) {
            if (command is AapCommand.SetDeviceName && preSendDeviceInfo != null) {
                _state.value = _state.value.copy(deviceInfo = preSendDeviceInfo)
            }
            throw e
        }
        coordinator.startVerification(
            command,
            scope!!,
            { _state.value },
            { cmd -> wrappedSend(sendRaw, cmd) },
            ::onVerificationOutcome
        )
    }

    private suspend fun wrappedSend(sendRaw: suspend (AapCommand) -> Unit, command: AapCommand) {
        sendRaw(command)
        lastSentCommand = command
        val now = timeSource.currentTimeMillis()
        lastSentAt = now
        if (command is AapCommand.SetAncMode) lastAncSentAt = now
    }

    private fun onVerificationOutcome(outcome: AapSettingsCoordinator.VerificationOutcome) {
        if (outcome is AapSettingsCoordinator.VerificationOutcome.Rejected) {
            val command = outcome.command
            if (command is AapCommand.SetAncMode && command.mode == AapSetting.AncMode.Value.OFF) {
                val prev = _state.value.setting<AapSetting.AllowOffOption>()
                if (prev == null || prev.enabled) {
                    _state.value = _state.value.withSetting(
                        AapSetting.AllowOffOption::class,
                        AapSetting.AllowOffOption(enabled = false),
                    )
                    log(TAG) { "Inferred: AllowOffOption = false (OFF mode rejected by device)" }
                }
            }
        }
    }

    // ── Message processing ──────────────────────────────────

    fun processMessage(message: AapMessage) {
        val hex = message.raw.joinToString(" ") { "%02X".format(it) }
        log(TAG, VERBOSE) { "MSG cmd=0x${"%04X".format(message.commandType)} len=${message.raw.size} raw=$hex" }

        // HANDSHAKING → READY: first non-settings-echo message means the device is talking.
        // Must happen before any early returns so decoded messages (battery, stem, etc.) also trigger it.
        if (!handshakeResponseReceived && message.commandType != 0x0009
            && _state.value.connectionState == AapPodState.ConnectionState.HANDSHAKING
        ) {
            handshakeResponseReceived = true
            _state.value = _state.value.copy(connectionState = AapPodState.ConnectionState.READY)
            log(TAG) { "Connection READY" }
        }

        // Issue #173 diagnostic dump
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
                    5 -> "firmwareVersionDup"
                    6 -> "protocolVersion"
                    7 -> "updaterAppId"
                    8 -> "leftEarbudSerial"
                    9 -> "rightEarbudSerial"
                    10 -> "buildNumber"
                    11 -> "encryptedBlob"
                    12 -> "timestamp"
                    else -> "unknown"
                }
                val rendered = seg.utf8?.let { "\"$it\"" } ?: "<non-utf8>"
                log(                    TAG, INFO                ) {
                    "DeviceInfoDump #173: [${seg.index}] off=${seg.offset} len=${seg.length} ($label) $rendered hex=${seg.hex}"
                }
            }
        }

        // Stem press (transient event, not stored in state)
        profile.decodeStemPress(message)?.let { event ->
            _stemPressEvents.tryEmit(event)
            log(TAG) { "Stem press: ${event.pressType} ${event.bud}" }
            return
        }

        // Battery
        profile.decodeBattery(message)?.let { batteries ->
            val valid = batteries.filterValues { it.charging != AapPodState.ChargingState.DISCONNECTED }
            _state.value = _state.value.copy(
                batteries = _state.value.batteries + valid,
                lastMessageAt = timeSource.now(),
            )
            log(TAG) { "Battery update: ${batteries.entries.map { "${it.key}=${(it.value.percent * 100).toInt()}% ${it.value.charging}" }}" }
            return
        }

        // Private key response
        profile.decodePrivateKeyResponse(message)?.let { keys ->
            log(TAG) { "Private keys received: IRK=${keys.irk != null}, ENC=${keys.encKey != null}" }
            _state.value = _state.value.copy(lastMessageAt = timeSource.now())
            _keysReceived.tryEmit(keys)
            return
        }

        // Device info
        profile.decodeDeviceInfo(message)?.let { info ->
            _state.value = _state.value.copy(deviceInfo = info, lastMessageAt = timeSource.now())
            log(TAG) { "Device info: ${info.name} (${info.modelNumber})" }
            return
        }

        // Setting update
        profile.decodeSetting(message)?.let { (key, value) ->
            val previous = _state.value.settings[key]

            // ANC debounce
            if (value is AapSetting.AncMode) {
                val isFirstAncMode = _state.value.setting<AapSetting.AncMode>() == null
                val recentAncSend = lastAncSentAt > 0L && (timeSource.currentTimeMillis() - lastAncSentAt) <= 3000L
                if (isFirstAncMode || recentAncSend) {
                    ancDebounceJob?.cancel()
                    _state.value = _state.value.withSetting(key, value).copy(lastMessageAt = timeSource.now())
                    log(TAG) { "Setting: ${key.simpleName} = $value [was: $previous]" }
                    applyInferences(value)
                } else {
                    ancDebounceJob?.cancel()
                    ancDebounceJob = scope?.launch {
                        delay(1500L)
                        _state.value = _state.value.withSetting(key, value).copy(lastMessageAt = timeSource.now())
                        log(TAG) { "Setting (debounced): ${key.simpleName} = $value [was: $previous]" }
                        applyInferences(value)
                    }
                }
                return
            }

            // Ear detection role swap
            val clearPrimaryPod = value is AapSetting.EarDetection && run {
                val prev = _state.value.setting<AapSetting.EarDetection>()
                prev != null && prev.primaryPod == value.secondaryPod && prev.secondaryPod == value.primaryPod
            }

            var newState = _state.value.withSetting(key, value).copy(lastMessageAt = timeSource.now())
            if (clearPrimaryPod) {
                newState = newState.copy(settings = newState.settings - AapSetting.PrimaryPod::class)
            }
            _state.value = newState
            log(TAG) { "Setting: ${key.simpleName} = $value${if (clearPrimaryPod) " (swap, PrimaryPod cleared)" else ""} [was: $previous]" }
            applyInferences(value)

            // Flush queued commands when pod goes in ear
            if (value is AapSetting.EarDetection && value.isEitherPodInEar) {
                val (commands, snapshot) = coordinator.flush()
                _state.value = _state.value.copy(
                    pendingAncMode = snapshot.pendingAncMode,
                    pendingSettingsCount = snapshot.count,
                )

                if (commands.isNotEmpty()) {
                    val ancCmd = commands.firstOrNull { it is AapCommand.SetAncMode } as? AapCommand.SetAncMode
                    if (ancCmd != null) {
                        val currentAnc = _state.value.setting<AapSetting.AncMode>()
                        if (currentAnc != null) {
                            _state.value = _state.value.withSetting(
                                    AapSetting.AncMode::class,
                                    currentAnc.copy(current = ancCmd.mode)
                                ).copy(lastMessageAt = timeSource.now())
                        }
                    }

                    log(TAG) { "Pod in ear, flushing ${commands.size} queued commands" }
                    val sendFn = activeSendRaw
                    scope?.launch {
                        if (sendFn == null) {
                            log(TAG, ERROR) { "No sendRaw available for flush" }
                            return@launch
                        }
                        for (cmd in commands) {
                            try {
                                wrappedSend(sendFn, cmd)
                            } catch (e: Exception) {
                                log(TAG, ERROR) { "Flush failed at ${cmd::class.simpleName}: $e" }
                                break
                            }
                        }
                        // Verify ANC mode if it was in the batch (most important for divergence detection).
                        // Fall back to verifying the last command if no ANC was flushed.
                        val toVerify = commands.firstOrNull { it is AapCommand.SetAncMode } ?: commands.lastOrNull()
                        toVerify?.let {
                            coordinator.startVerification(
                                it,
                                this,
                                { _state.value },
                                { cmd -> wrappedSend(sendFn, cmd) },
                                ::onVerificationOutcome
                            )
                        }
                    }
                }
            }

            return
        }

        val payloadHex = message.payload.joinToString(" ") { "%02X".format(it) }
        val sinceSend = if (lastSentAt == 0L) -1L else timeSource.currentTimeMillis() - lastSentAt
        val lastSend = lastSentCommand?.let { it::class.simpleName } ?: "none"

        if (message.commandType == 0x0009 && message.payload.size >= 2) {
            val settingId = message.payload[0].toInt() and 0xFF
            val value = message.payload[1].toInt() and 0xFF
            val boolHint = value.appleBoolHint()
            val tailHex = if (message.payload.size > 2) {
                message.payload.copyOfRange(2, message.payload.size).joinToString(" ") { "%02X".format(it) }
            } else {
                ""
            }
            log(TAG, INFO) {
                buildString {
                    append("Unhandled setting id=0x${"%02X".format(settingId)} ")
                    append("value=0x${"%02X".format(value)}")
                    boolHint?.let { append(" appleBool=$it") }
                    append(" payload=${message.payload.size}B")
                    if (tailHex.isNotEmpty()) append(" tail=[$tailHex]")
                    append(" sinceLastSend=${sinceSend}ms lastSend=$lastSend")
                }
            }
            return
        }

        if (message.commandType == 0x000C && message.payload.size >= 6) {
            val macRaw = message.payload.formatMac(reverse = false)
            val macReversed = message.payload.formatMac(reverse = true)
            val tailHex = if (message.payload.size > 6) {
                message.payload.copyOfRange(6, message.payload.size).joinToString(" ") { "%02X".format(it) }
            } else {
                ""
            }
            _state.value = _state.value.copy(lastMessageAt = timeSource.now())
            log(TAG, VERBOSE) {
                buildString {
                    append("Known cmd=0x000C")
                    append(" payload=${message.payload.size}B")
                    append(" macRaw=$macRaw macReversed=$macReversed")
                    if (tailHex.isNotEmpty()) append(" tail=[$tailHex]")
                    append(" sinceLastSend=${sinceSend}ms lastSend=$lastSend")
                }
            }
            return
        }

        if (message.commandType in KNOWN_NON_SETTINGS_COMMANDS) {
            _state.value = _state.value.copy(lastMessageAt = timeSource.now())
            log(TAG, VERBOSE) {
                "Known cmd=0x${"%04X".format(message.commandType)} payload=${message.payload.size}B [$payloadHex] sinceLastSend=${sinceSend}ms lastSend=$lastSend"
            }
            return
        }

        log(TAG, INFO) {
            "Unhandled cmd=0x${"%04X".format(message.commandType)} payload=${message.payload.size}B [$payloadHex] sinceLastSend=${sinceSend}ms lastSend=$lastSend"
        }
    }

    // ── Inference ───────────────────────────────────────────

    private fun applyInferences(trigger: AapSetting) {
        val inferred = mutableListOf<Pair<KClass<out AapSetting>, AapSetting>>()

        if (trigger is AapSetting.AncMode && trigger.current == AapSetting.AncMode.Value.OFF) {
            val current = _state.value.setting<AapSetting.AllowOffOption>()
            if (current == null || !current.enabled) {
                inferred += AapSetting.AllowOffOption::class to AapSetting.AllowOffOption(enabled = true)
            }
        }

        for ((key, value) in inferred) {
            _state.value = _state.value.withSetting(key, value)
            log(TAG) { "Inferred: ${key.simpleName} = $value (from ${trigger::class.simpleName})" }
        }
    }

    companion object {
        private val TAG = logTag("AAP", "Engine")

        private val KNOWN_NON_SETTINGS_COMMANDS = setOf(
            0x0000, 0x0002, 0x0017, 0x002B, 0x004E, 0x0052, 0x0055, 0x0057,
        )

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
                        .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                        .decode(java.nio.ByteBuffer.wrap(segBytes))
                        .toString()
                } catch (_: java.nio.charset.CharacterCodingException) {
                    null
                }
                val hex = segBytes.joinToString("") { "%02X".format(it) }

                segments.add(DeviceInfoSegment(segIndex, segStart, segBytes.size, utf8, hex))
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

// ── Extension helpers ───────────────────────────────────

/** Apple wire-format boolean: 0x01 = true, 0x02 = false. */
private fun Int.appleBoolHint(): Boolean? = when (this) {
    0x01 -> true
    0x02 -> false
    else -> null
}

/** Format 6-byte MAC address, optionally reversed. */
private fun ByteArray.formatMac(reverse: Boolean): String {
    val indices = if (reverse) (5 downTo 0) else (0..5)
    return indices.joinToString(":") { "%02X".format(this[it]) }
}
