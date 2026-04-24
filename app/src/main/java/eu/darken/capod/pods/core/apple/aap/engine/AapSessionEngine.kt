package eu.darken.capod.pods.core.apple.aap.engine

import eu.darken.capod.common.TimeSource
import eu.darken.capod.common.debug.logging.Logging.Priority.ERROR
import eu.darken.capod.common.debug.logging.Logging.Priority.INFO
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.pods.core.apple.aap.AapPodState
import eu.darken.capod.pods.core.apple.aap.protocol.AapCommand
import eu.darken.capod.pods.core.apple.aap.protocol.AapDeviceProfile
import eu.darken.capod.pods.core.apple.aap.protocol.AapMessage
import eu.darken.capod.pods.core.apple.aap.protocol.AapMessageType
import eu.darken.capod.pods.core.apple.aap.protocol.AapPacket
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import eu.darken.capod.pods.core.apple.aap.protocol.AapSleepEvent
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
    profile: AapDeviceProfile,
    private val timeSource: TimeSource,
) {

    private val _state = MutableStateFlow(AapPodState())
    val state: StateFlow<AapPodState> = _state.asStateFlow()

    private val _keysReceived = MutableSharedFlow<KeyExchangeResult>(extraBufferCapacity = 1)
    val keysReceived: SharedFlow<KeyExchangeResult> = _keysReceived.asSharedFlow()

    private val _stemPressEvents =
        MutableSharedFlow<StemPressEvent>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val stemPressEvents: SharedFlow<StemPressEvent> = _stemPressEvents.asSharedFlow()

    private val _sleepEvents =
        MutableSharedFlow<AapSleepEvent>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val sleepEvents: SharedFlow<AapSleepEvent> = _sleepEvents.asSharedFlow()

    private val _offRejected =
        MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val offRejected: SharedFlow<Unit> = _offRejected.asSharedFlow()

    /**
     * Fires whenever a write command fails verification after the coordinator's single retry —
     * i.e. the device neither echoed the expected state nor retried into a matching one.
     * Separate from [offRejected] which is specialised for the ANC-OFF UX path. Consumers filter
     * by the command type they care about (e.g. the charge-cap toggle shows a snackbar).
     */
    private val _settingRejected =
        MutableSharedFlow<AapCommand>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val settingRejected: SharedFlow<AapCommand> = _settingRejected.asSharedFlow()

    private val hidTracker = HidTracker { msg -> log(TAG) { msg } }
    private val inboundInterpreter = AapInboundInterpreter(profile)
    private val ancController = AapAncController()
    private val outboundController = AapOutboundController(timeSource)

    private val sendMutex = Mutex()
    private val timerJobs = mutableMapOf<EngineTimerKey, Job>()

    private var scope: CoroutineScope? = null
    private var activeSendRaw: (suspend (AapCommand) -> Unit)? = null
    private var runtimeState = EngineRuntimeState()

    fun start(scope: CoroutineScope) {
        dispatch(AapEngineEvent.SessionStarted(scope))
    }

    fun onHandshakeSent() {
        dispatch(AapEngineEvent.HandshakeSent)
    }

    fun reset() {
        dispatch(AapEngineEvent.ResetRequested)
    }

    suspend fun send(command: AapCommand, sendRaw: suspend (AapCommand) -> Unit) {
        scope ?: throw IllegalStateException("Cannot send command without an active session scope")
        sendMutex.withLock {
            activeSendRaw = sendRaw
            val decision = outboundController.onCommandRequested(_state.value, runtimeState.outbound, command)
            applyOutboundDecisionInline(decision)
        }
    }

    fun processMessage(message: AapMessage) {
        dispatch(AapEngineEvent.MessageReceived(message))
    }

    /**
     * Handle a Connect Response (packet type 0x0001) from the peer. Stores
     * the 64-bit features bitmask in [AapPodState.negotiatedFeatures] for
     * future correlation work, but does NOT drive state transitions itself —
     * the first subsequent Message packet triggers HANDSHAKING → READY via
     * the existing logic.
     *
     * On non-zero status we log an error and skip the features update; the
     * engine stays in HANDSHAKING (no probes should fire until the session
     * succeeds).
     */
    fun processConnectResponse(packet: AapPacket.ConnectResponse) {
        dispatch(AapEngineEvent.ConnectResponseReceived(packet))
    }

    private fun dispatch(event: AapEngineEvent) {
        when (event) {
            is AapEngineEvent.SessionStarted -> {
                scope = event.scope
                runtimeState = runtimeState.copy(handshakeResponseReceived = false)
                _state.value = _state.value.copy(connectionState = AapPodState.ConnectionState.CONNECTING)
            }

            AapEngineEvent.HandshakeSent -> {
                runtimeState = runtimeState.copy(handshakeResponseReceived = false)
                _state.value = _state.value.copy(connectionState = AapPodState.ConnectionState.HANDSHAKING)
            }

            AapEngineEvent.ResetRequested -> {
                cancelAllTimers()
                hidTracker.flush()
                hidTracker.reset()
                scope = null
                activeSendRaw = null
                runtimeState = EngineRuntimeState()
                _state.value = AapPodState(connectionState = AapPodState.ConnectionState.DISCONNECTED)
            }

            is AapEngineEvent.MessageReceived -> handleMessageReceived(event.message)
            is AapEngineEvent.ConnectResponseReceived -> handleConnectResponse(event.packet)
            is AapEngineEvent.InboundUpdateDecoded -> handleInboundUpdate(event.update)
            is AapEngineEvent.TimerFired -> handleTimerFired(event.key)
        }
    }

    private fun handleConnectResponse(packet: AapPacket.ConnectResponse) {
        if (packet.status != 0) {
            log(TAG, ERROR) {
                "ConnectResponse failed: status=0x${"%04X".format(packet.status)} " +
                        "major=${packet.major} minor=${packet.minor} " +
                        "features=0x${"%016X".format(packet.features.toLong())}"
            }
            _state.value = _state.value.copy(connectResponseStatus = packet.status)
            return
        }

        log(TAG, INFO) {
            "ConnectResponse OK: major=${packet.major} minor=${packet.minor} " +
                    "features=0x${"%016X".format(packet.features.toLong())}"
        }
        _state.value = _state.value.copy(
            negotiatedFeatures = packet.features,
            connectResponseStatus = packet.status,
            lastMessageAt = timeSource.now(),
        )
    }

    private fun handleMessageReceived(message: AapMessage) {
        if (message.commandType != AapMessageType.BUDDY_COMMAND.value) {
            val hex = message.raw.joinToString(" ") { "%02X".format(it) }
            log(TAG, VERBOSE) {
                "MSG cmd=0x${"%04X".format(message.commandType)} len=${message.raw.size} raw=$hex"
            }
            hidTracker.flush()
        }

        if (!runtimeState.handshakeResponseReceived &&
            message.commandType != AapMessageType.CONTROL.value &&
            _state.value.connectionState == AapPodState.ConnectionState.HANDSHAKING
        ) {
            runtimeState = runtimeState.copy(handshakeResponseReceived = true)
            _state.value = _state.value.copy(connectionState = AapPodState.ConnectionState.READY)
            log(TAG) { "Connection READY" }
        }

        if (message.commandType == AapMessageType.BUDDY_COMMAND.value) {
            hidTracker.consume(message.payload)
            _state.value = _state.value.copy(lastMessageAt = timeSource.now())
            return
        }

        if (message.commandType == AapMessageType.INFORMATION.value) {
            logDeviceInfoDiagnostics(message.payload)
        }

        val update = inboundInterpreter.decode(message)
        if (update != null) {
            dispatch(AapEngineEvent.InboundUpdateDecoded(update))
        } else {
            logUnhandledMessage(message)
        }
    }

    private fun handleInboundUpdate(update: AapInboundUpdate) {
        when (update) {
            is AapInboundUpdate.StemPress -> {
                _stemPressEvents.tryEmit(update.event)
                log(TAG) { "Stem press: ${update.event.pressType} ${update.event.bud}" }
            }

            is AapInboundUpdate.Battery -> {
                val valid = update.batteries.filterValues {
                    it.charging != AapPodState.ChargingState.DISCONNECTED
                }
                _state.value = _state.value.copy(
                    batteries = _state.value.batteries + valid,
                    lastMessageAt = timeSource.now(),
                )
                log(TAG) {
                    "Battery update: ${update.batteries.entries.map { "${it.key}=${(it.value.percent * 100).toInt()}% ${it.value.charging}" }}"
                }
            }

            is AapInboundUpdate.PrivateKeys -> {
                log(TAG) {
                    "Private keys received: IRK=${update.result.irk != null}, ENC=${update.result.encKey != null}"
                }
                _state.value = _state.value.copy(lastMessageAt = timeSource.now())
                _keysReceived.tryEmit(update.result)
            }

            is AapInboundUpdate.DeviceInfo -> {
                _state.value = _state.value.copy(deviceInfo = update.info, lastMessageAt = timeSource.now())
                log(TAG) { "Device info: ${update.info.name} (${update.info.modelNumber})" }
            }

            is AapInboundUpdate.CaseInfo -> {
                _state.value = _state.value.copy(caseInfo = update.info, lastMessageAt = timeSource.now())
                val hex = update.info.rawPayload.joinToString(" ") { "%02X".format(it) }
                log(TAG, INFO) { "Case info: ${update.info.rawPayload.size}B payload=[$hex]" }
            }

            is AapInboundUpdate.SleepEvent -> {
                _state.value = _state.value.copy(lastMessageAt = timeSource.now())
                val hex = update.event.rawPayload.joinToString(" ") { "%02X".format(it) }
                log(TAG, INFO) { "Sleep event: ${update.event.rawPayload.size}B payload=[$hex]" }
                _sleepEvents.tryEmit(update.event)
            }

            is AapInboundUpdate.DynamicEndOfChargeEvent -> {
                _state.value = _state.value.copy(lastMessageAt = timeSource.now())
                val hex = update.event.rawPayload.joinToString(" ") { "%02X".format(it) }
                log(TAG, INFO) { "Dynamic EoC event: ${update.event.rawPayload.size}B payload=[$hex]" }
            }

            is AapInboundUpdate.Setting -> handleSettingUpdate(update.key, update.value)
        }
    }

    private fun handleSettingUpdate(key: KClass<out AapSetting>, value: AapSetting) {
        if (value is AapSetting.AncMode) {
            val decision = ancController.onAncSetting(
                podState = _state.value,
                runtimeState = runtimeState.anc,
                key = key,
                value = value,
                isRecentAncSend = isRecentAncSend(),
                now = timeSource.now(),
            )
            applyAncDecision(decision)
            return
        }

        val previous = _state.value.settings[key]
        val clearPrimaryPod = value is AapSetting.EarDetection && run {
            val prev = _state.value.setting<AapSetting.EarDetection>()
            prev != null && prev.primaryPod == value.secondaryPod && prev.secondaryPod == value.primaryPod
        }

        var newState = _state.value.withSetting(key, value).copy(lastMessageAt = timeSource.now())
        if (clearPrimaryPod) {
            newState = newState.copy(settings = newState.settings - AapSetting.PrimaryPod::class)
        }
        _state.value = newState
        log(TAG) {
            "Setting: ${key.simpleName} = $value${if (clearPrimaryPod) " (swap, PrimaryPod cleared)" else ""} [was: $previous]"
        }

        if (value is AapSetting.EarDetection) {
            applyAncDecision(ancController.onEarDetectionUpdated(_state.value, runtimeState.anc))
            if (value.isEitherPodInEar) {
                applyOutboundDecisionAsync(
                    outboundController.onEarDetectionInEar(_state.value, runtimeState.outbound),
                )
            }
        }
    }

    private fun handleTimerFired(key: EngineTimerKey) {
        when (key) {
            EngineTimerKey.AncDebounce -> {
                val decision = ancController.onAncDebounceTimerFired(
                    podState = _state.value,
                    runtimeState = runtimeState.anc,
                    now = timeSource.now(),
                    isRecentAncSend = isRecentAncSend(),
                )
                applyAncDecision(decision)
            }

            EngineTimerKey.AllowOffInference -> {
                applyAncDecision(ancController.onAllowOffInferenceTimerFired(_state.value, runtimeState.anc))
            }

            EngineTimerKey.Verification -> {
                applyOutboundDecisionAsync(
                    outboundController.onVerificationTimerFired(_state.value, runtimeState.outbound),
                )
            }
        }
    }

    private fun applyAncDecision(decision: AncDecision) {
        _state.value = decision.podState
        runtimeState = runtimeState.copy(anc = decision.runtimeState)
        decision.logs.forEach { log(TAG) { it } }
        applyTimerActions(decision.timerActions)
    }

    /**
     * Apply the non-send side of a decision (state, runtime, logs) and prepare the send context.
     *
     * Returns `null` when there's nothing to send — either the decision carries no commands, or
     * no transport is currently available. In both "nothing to send" paths, the decision's timer
     * actions and rejection handling are applied before returning, so callers only need to worry
     * about the send path itself.
     */
    private fun applyDecisionStateAndPrepareSend(decision: OutboundDecision): OutboundSendContext? {
        val previousState = _state.value
        val previousVerification = runtimeState.outbound.verification

        _state.value = decision.podState
        runtimeState = runtimeState.copy(outbound = decision.runtimeState)
        decision.logs.forEach { log(TAG) { it } }

        if (decision.commandsToSend.isEmpty()) {
            applyTimerActions(decision.timerActions)
            handleRejectedCommand(decision.rejectedCommand)
            return null
        }

        val sendFn = activeSendRaw
        if (sendFn == null) {
            log(TAG, ERROR) { "No sendRaw available for outbound send" }
            restoreVerification(previousVerification)
            return null
        }

        return OutboundSendContext(sendFn, previousState, previousVerification, decision)
    }

    private fun restoreVerification(previousVerification: VerificationState?) {
        runtimeState = runtimeState.copy(outbound = runtimeState.outbound.copy(verification = previousVerification))
    }

    /** User-initiated send: runs in the caller's coroutine, errors propagate back to the caller. */
    private suspend fun applyOutboundDecisionInline(decision: OutboundDecision) {
        val ctx = applyDecisionStateAndPrepareSend(decision) ?: return
        try {
            sendCommands(ctx.sendFn, ctx.decision.commandsToSend, ctx.previousState)
            applyTimerActions(ctx.decision.timerActions)
            handleRejectedCommand(ctx.decision.rejectedCommand)
        } catch (e: Exception) {
            restoreVerification(ctx.previousVerification)
            throw e
        }
    }

    /** Engine-initiated send (timer fired, ear-detection flush): launched on the engine's scope. */
    private fun applyOutboundDecisionAsync(decision: OutboundDecision) {
        val ctx = applyDecisionStateAndPrepareSend(decision) ?: return
        val currentScope = scope
        if (currentScope == null) {
            log(TAG, ERROR) { "No scope available for outbound async send" }
            restoreVerification(ctx.previousVerification)
            return
        }
        currentScope.launch {
            try {
                sendCommands(ctx.sendFn, ctx.decision.commandsToSend, ctx.previousState)
                applyTimerActions(ctx.decision.timerActions)
                handleRejectedCommand(ctx.decision.rejectedCommand)
            } catch (_: Exception) {
                restoreVerification(ctx.previousVerification)
            }
        }
    }

    private suspend fun sendCommands(
        sendFn: suspend (AapCommand) -> Unit,
        commands: List<AapCommand>,
        previousState: AapPodState,
    ) {
        commands.forEach { command ->
            try {
                wrappedSend(sendFn, command)
            } catch (e: Exception) {
                if (command is AapCommand.SetDeviceName && previousState.deviceInfo != null) {
                    _state.value = _state.value.copy(deviceInfo = previousState.deviceInfo)
                }
                if (commands.size == 1) {
                    throw e
                }
                log(TAG, ERROR) { "Flush failed at ${command::class.simpleName}: $e" }
                throw e
            }
        }
    }

    private suspend fun wrappedSend(sendFn: suspend (AapCommand) -> Unit, command: AapCommand) {
        sendFn(command)
        val now = timeSource.currentTimeMillis()
        runtimeState = runtimeState.copy(
            lastSentCommand = command,
            lastSentAtMs = now,
            lastAncSentAtMs = if (command is AapCommand.SetAncMode) now else runtimeState.lastAncSentAtMs,
        )
    }

    private fun handleRejectedCommand(command: AapCommand?) {
        if (command == null) return
        if (command is AapCommand.SetAncMode && command.mode == AapSetting.AncMode.Value.OFF) {
            applyAncDecision(ancController.onOffRejected(_state.value, runtimeState.anc))
            _offRejected.tryEmit(Unit)
        }
        _settingRejected.tryEmit(command)
    }

    private fun scheduleTimer(key: EngineTimerKey, delayMs: Long) {
        val currentScope = scope ?: return
        timerJobs.remove(key)?.cancel()
        timerJobs[key] = currentScope.launch {
            delay(delayMs)
            timerJobs.remove(key)
            dispatch(AapEngineEvent.TimerFired(key))
        }
    }

    private fun cancelTimer(key: EngineTimerKey) {
        timerJobs.remove(key)?.cancel()
    }

    private fun cancelAllTimers() {
        timerJobs.values.forEach { it.cancel() }
        timerJobs.clear()
    }

    private fun applyTimerActions(actions: List<EngineTimerAction>) {
        actions.forEach { action ->
            when (action) {
                is EngineTimerAction.Start -> scheduleTimer(action.key, action.delayMs)
                is EngineTimerAction.Cancel -> cancelTimer(action.key)
            }
        }
    }

    private fun isRecentAncSend(): Boolean =
        runtimeState.lastAncSentAtMs > 0L && (timeSource.currentTimeMillis() - runtimeState.lastAncSentAtMs) <= 3000L

    private fun currentSendDebugInfo(): SendDebugInfo {
        val sinceLastSend =
            if (runtimeState.lastSentAtMs == 0L) -1L else timeSource.currentTimeMillis() - runtimeState.lastSentAtMs
        val lastSend = runtimeState.lastSentCommand?.let { it::class.simpleName } ?: "none"
        return SendDebugInfo(sinceLastSend = sinceLastSend, lastSend = lastSend)
    }

    private fun logDeviceInfoDiagnostics(payload: ByteArray) {
        val segments = AapDeviceInfoDiagnostics.describeSegments(payload)
        log(TAG, INFO) {
            "DeviceInfoDump #173: payload=${payload.size} bytes, segments=${segments.size}"
        }
        segments.forEach { seg ->
            // Labels per the Wireshark AAP dissector. The segmenter knows the schema —
            // segments 11 and 12 are fixed 17-byte UUIDs.
            val label = when (seg.index) {
                0 -> "name"
                1 -> "modelNumber"
                2 -> "manufacturer"
                3 -> "serialNumber"
                4 -> "firmwareVersion"
                5 -> "firmwareVersionPending"
                6 -> "hardwareVersion"
                7 -> "eaProtocolName"
                8 -> "leftEarbudSerial"
                9 -> "rightEarbudSerial"
                10 -> "marketingVersion"
                11 -> "leftEarbudUuid (17 bytes fixed)"
                12 -> "rightEarbudUuid (17 bytes fixed)"
                13 -> "leftEarbudFirstPaired"
                14 -> "rightEarbudFirstPaired"
                else -> "unknown"
            }
            val rendered = seg.utf8?.let { "\"$it\"" } ?: "<non-utf8>"
            log(TAG, INFO) {
                "DeviceInfoDump #173: [${seg.index}] off=${seg.offset} len=${seg.length} ($label) $rendered hex=${seg.hex}"
            }
        }
    }

    private fun logUnhandledMessage(message: AapMessage) {
        val payloadHex = message.payload.joinToString(" ") { "%02X".format(it) }
        val sendInfo = currentSendDebugInfo()

        if (message.commandType == AapMessageType.CONTROL.value && message.payload.size >= 2) {
            val settingId = message.payload[0].toInt() and 0xFF
            val value = message.payload[1].toInt() and 0xFF
            val boolHint = value.appleBoolHint()
            val tailHex = if (message.payload.size > 2) {
                message.payload.copyOfRange(2, message.payload.size)
                    .joinToString(" ") { "%02X".format(it) }
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
                    append(" sinceLastSend=${sendInfo.sinceLastSend}ms lastSend=${sendInfo.lastSend}")
                }
            }
            return
        }

        if (message.commandType == AapMessageType.MAC_ADDRESS.value && message.payload.size >= 6) {
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
                    append("Known cmd=0x000C (${AapMessageType.MAC_ADDRESS.wiresharkName})")
                    append(" payload=${message.payload.size}B")
                    append(" macRaw=$macRaw macReversed=$macReversed")
                    if (tailHex.isNotEmpty()) append(" tail=[$tailHex]")
                    append(" sinceLastSend=${sendInfo.sinceLastSend}ms lastSend=${sendInfo.lastSend}")
                }
            }
            return
        }

        if (message.commandType in KNOWN_NON_SETTINGS_COMMANDS) {
            _state.value = _state.value.copy(lastMessageAt = timeSource.now())
            val namedType = AapMessageType.byValue(message.commandType)
            val nameLabel = namedType?.let { " (${it.wiresharkName})" } ?: ""
            log(TAG, VERBOSE) {
                "Known cmd=0x${"%04X".format(message.commandType)}$nameLabel payload=${message.payload.size}B [$payloadHex] sinceLastSend=${sendInfo.sinceLastSend}ms lastSend=${sendInfo.lastSend}"
            }
            return
        }

        val namedType = AapMessageType.byValue(message.commandType)
        val nameLabel = namedType?.let { " (${it.wiresharkName})" } ?: " (unknown)"
        log(TAG, INFO) {
            "Unhandled cmd=0x${"%04X".format(message.commandType)}$nameLabel payload=${message.payload.size}B [$payloadHex] sinceLastSend=${sendInfo.sinceLastSend}ms lastSend=${sendInfo.lastSend}"
        }
    }

    companion object {
        private val TAG = logTag("AAP", "Engine")

        /**
         * Opcodes we see on-wire but don't model as a domain update. Decoded only
         * enough to refresh `lastMessageAt` (freshness feeds the AAP quality boost
         * in PodDevice.computeAapBoost). Add newly-catalogued push-only opcodes here
         * when they appear in captures so logs get a named label AND freshness stays
         * intact.
         */
        private val KNOWN_NON_SETTINGS_COMMANDS: Set<Int> = setOf(
            0x0000,                                             // Connect (shouldn't reach here but historically observed)
            AapMessageType.CAPABILITIES.value,                  // 0x0002
            AapMessageType.DEVICE_LIST.value,                   // 0x000B
            AapMessageType.TRIANGLE_STATUS_REQUEST.value,       // 0x0015
            AapMessageType.MAGNET_LINK.value,                   // 0x0016
            AapMessageType.TIMESTAMP.value,                     // 0x001B
            AapMessageType.UNKNOWN_0X21.value,                  // 0x0021
            AapMessageType.CASE_INFO.value,                     // 0x0023 (handled via decoder later; still refresh)
            AapMessageType.GYRO_INFO.value,                     // 0x0028
            AapMessageType.STREAM_STATE_INFO.value,             // 0x002B
            AapMessageType.GAPA_CHALLENGE.value,                // 0x002C
            AapMessageType.UNKNOWN_0X40.value,                  // 0x0040
            AapMessageType.ADAPTIVE_VOLUME_MESSAGE.value,       // 0x004C
            AapMessageType.SOURCE_FEATURE_CAPABILITIES.value,   // 0x004D
            AapMessageType.FEATURE_PROX_CARD_STATUS_UPDATE.value, // 0x004E
            AapMessageType.UARP_DATA.value,                     // 0x004F
            AapMessageType.UNKNOWN_0X50.value,                  // 0x0050
            AapMessageType.SOURCE_CONTEXT.value,                // 0x0052
            AapMessageType.SET_BAND_EDGES.value,                // 0x0054 RF band gating (push-only; see AapMessageType kdoc)
            AapMessageType.UNKNOWN_0X55.value,                  // 0x0055
            AapMessageType.SLEEP_DETECTION_UPDATE.value,        // 0x0057
            AapMessageType.UNKNOWN_0X58.value,                  // 0x0058
            AapMessageType.DYNAMIC_END_OF_CHARGE.value,         // 0x0059
            AapMessageType.PERSONAL_TRANSLATION.value,          // 0x0060
        )
    }
}

/**
 * Synchronous engine events. User-initiated sends are intentionally not modeled here — they
 * require a suspending transport callback and are handled directly in [AapSessionEngine.send].
 */
internal sealed interface AapEngineEvent {
    data class SessionStarted(val scope: CoroutineScope) : AapEngineEvent
    data object HandshakeSent : AapEngineEvent
    data object ResetRequested : AapEngineEvent
    data class MessageReceived(val message: AapMessage) : AapEngineEvent
    data class ConnectResponseReceived(val packet: AapPacket.ConnectResponse) : AapEngineEvent
    data class InboundUpdateDecoded(val update: AapInboundUpdate) : AapEngineEvent
    data class TimerFired(val key: EngineTimerKey) : AapEngineEvent
}

internal data class EngineRuntimeState(
    val handshakeResponseReceived: Boolean = false,
    val lastSentCommand: AapCommand? = null,
    val lastSentAtMs: Long = 0L,
    val lastAncSentAtMs: Long = 0L,
    val outbound: OutboundRuntimeState = OutboundRuntimeState(),
    val anc: AncRuntimeState = AncRuntimeState(),
)

internal sealed interface EngineTimerKey {
    data object AncDebounce : EngineTimerKey
    data object AllowOffInference : EngineTimerKey
    data object Verification : EngineTimerKey
}

internal sealed interface EngineTimerAction {
    data class Start(val key: EngineTimerKey, val delayMs: Long) : EngineTimerAction
    data class Cancel(val key: EngineTimerKey) : EngineTimerAction
}

private data class OutboundSendContext(
    val sendFn: suspend (AapCommand) -> Unit,
    val previousState: AapPodState,
    val previousVerification: VerificationState?,
    val decision: OutboundDecision,
)

private data class SendDebugInfo(
    val sinceLastSend: Long,
    val lastSend: String,
)

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
