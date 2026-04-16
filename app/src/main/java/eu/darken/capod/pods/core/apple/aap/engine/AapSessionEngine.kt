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
            is AapEngineEvent.InboundUpdateDecoded -> handleInboundUpdate(event.update)
            is AapEngineEvent.TimerFired -> handleTimerFired(event.key)
        }
    }

    private fun handleMessageReceived(message: AapMessage) {
        if (message.commandType != CMD_HID_DESCRIPTOR) {
            val hex = message.raw.joinToString(" ") { "%02X".format(it) }
            log(TAG, VERBOSE) {
                "MSG cmd=0x${"%04X".format(message.commandType)} len=${message.raw.size} raw=$hex"
            }
            hidTracker.flush()
        }

        if (!runtimeState.handshakeResponseReceived &&
            message.commandType != CMD_SETTING &&
            _state.value.connectionState == AapPodState.ConnectionState.HANDSHAKING
        ) {
            runtimeState = runtimeState.copy(handshakeResponseReceived = true)
            _state.value = _state.value.copy(connectionState = AapPodState.ConnectionState.READY)
            log(TAG) { "Connection READY" }
        }

        if (message.commandType == CMD_HID_DESCRIPTOR) {
            hidTracker.consume(message.payload)
            _state.value = _state.value.copy(lastMessageAt = timeSource.now())
            return
        }

        if (message.commandType == CMD_DEVICE_INFO) {
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
        if (command is AapCommand.SetAncMode && command.mode == AapSetting.AncMode.Value.OFF) {
            applyAncDecision(ancController.onOffRejected(_state.value, runtimeState.anc))
        }
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
            log(TAG, INFO) {
                "DeviceInfoDump #173: [${seg.index}] off=${seg.offset} len=${seg.length} ($label) $rendered hex=${seg.hex}"
            }
        }
    }

    private fun logUnhandledMessage(message: AapMessage) {
        val payloadHex = message.payload.joinToString(" ") { "%02X".format(it) }
        val sendInfo = currentSendDebugInfo()

        if (message.commandType == CMD_SETTING && message.payload.size >= 2) {
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

        if (message.commandType == CMD_CONNECTED_DEVICE && message.payload.size >= 6) {
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
                    append(" sinceLastSend=${sendInfo.sinceLastSend}ms lastSend=${sendInfo.lastSend}")
                }
            }
            return
        }

        if (message.commandType in KNOWN_NON_SETTINGS_COMMANDS) {
            _state.value = _state.value.copy(lastMessageAt = timeSource.now())
            log(TAG, VERBOSE) {
                "Known cmd=0x${"%04X".format(message.commandType)} payload=${message.payload.size}B [$payloadHex] sinceLastSend=${sendInfo.sinceLastSend}ms lastSend=${sendInfo.lastSend}"
            }
            return
        }

        log(TAG, INFO) {
            "Unhandled cmd=0x${"%04X".format(message.commandType)} payload=${message.payload.size}B [$payloadHex] sinceLastSend=${sendInfo.sinceLastSend}ms lastSend=${sendInfo.lastSend}"
        }
    }

    companion object {
        private val TAG = logTag("AAP", "Engine")
        private const val CMD_SETTING = 0x0009
        private const val CMD_CONNECTED_DEVICE = 0x000C
        private const val CMD_DEVICE_INFO = 0x001D
        private const val CMD_HID_DESCRIPTOR = 0x0017

        private val KNOWN_NON_SETTINGS_COMMANDS = setOf(
            0x0000, 0x0002, 0x002B, 0x004E, 0x0052, 0x0055, 0x0057,
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
