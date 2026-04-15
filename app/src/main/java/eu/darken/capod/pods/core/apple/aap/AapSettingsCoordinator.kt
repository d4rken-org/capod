package eu.darken.capod.pods.core.apple.aap

import eu.darken.capod.common.TimeSource
import eu.darken.capod.common.debug.logging.Logging.Priority.ERROR
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.pods.core.apple.aap.protocol.AapCommand
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import kotlin.reflect.KClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Coordinates deferred outbound settings and post-send verification.
 * Does not own transport — [AapConnection] keeps socket and state mutation responsibility.
 *
 * All setting commands (except [AapCommand.SetDeviceName]) can be deferred here when no pod
 * is in ear, then flushed as a batch when a pod goes in.
 */
internal class AapSettingsCoordinator(
    private val timeSource: TimeSource,
) {

    /** Snapshot of pending state — returned from every mutating method. */
    data class PendingSnapshot(
        val pendingAncMode: AapSetting.AncMode.Value?,
        val count: Int,
    )

    /** Verification result — caller handles domain inference (e.g. AllowOffOption). */
    sealed class VerificationOutcome {
        data object Confirmed : VerificationOutcome()
        data class Rejected(val command: AapCommand) : VerificationOutcome()
    }

    // Keyed by command class — newer commands of the same type overwrite.
    private val pendingCommands = linkedMapOf<KClass<out AapCommand>, AapCommand>()
    private var verificationJob: Job? = null

    // ── Queue operations ────────────────────────────────────

    /**
     * Queue a command. Returns (optimistic state update or null, pending snapshot).
     * ANC uses [PendingSnapshot.pendingAncMode] for display, so no optimistic setting update.
     * Handles stale dependency: removes [AapCommand.SetAdaptiveAudioNoise] when ANC leaves ADAPTIVE.
     */
    fun enqueue(command: AapCommand, currentState: AapPodState): Pair<AapPodState?, PendingSnapshot> {
        if (command is AapCommand.SetAncMode && command.mode != AapSetting.AncMode.Value.ADAPTIVE) {
            synchronized(pendingCommands) { pendingCommands.remove(AapCommand.SetAdaptiveAudioNoise::class) }
        }
        synchronized(pendingCommands) { pendingCommands[command::class] = command }

        val optimistic = if (command !is AapCommand.SetAncMode) {
            optimisticUpdate(currentState, command)
        } else {
            null
        }
        return optimistic to snapshot()
    }

    /** Remove a specific command class from the queue. */
    fun removeFromQueue(commandClass: KClass<out AapCommand>): PendingSnapshot {
        synchronized(pendingCommands) { pendingCommands.remove(commandClass) }
        return snapshot()
    }

    /** Return sorted pending commands (ANC first) and clear queue. */
    fun flush(): Pair<List<AapCommand>, PendingSnapshot> {
        val commands: List<AapCommand>
        synchronized(pendingCommands) {
            commands = pendingCommands.values.toList()
            pendingCommands.clear()
        }
        // Dependency-aware ordering:
        // AllowOffOption must precede AncMode (device rejects OFF without it)
        // AncMode must precede mode-dependent settings (e.g. AdaptiveAudioNoise)
        val sorted = commands.sortedBy {
            when (it) {
                is AapCommand.SetAllowOffOption -> 0
                is AapCommand.SetAncMode -> 1
                else -> 2
            }
        }
        return sorted to snapshot()
    }

    /** Cancel verification, clear queue. */
    fun clear(): PendingSnapshot {
        verificationJob?.cancel()
        verificationJob = null
        synchronized(pendingCommands) { pendingCommands.clear() }
        return snapshot()
    }

    private fun snapshot(): PendingSnapshot {
        val map = synchronized(pendingCommands) { pendingCommands.toMap() }
        val ancPending = (map[AapCommand.SetAncMode::class] as? AapCommand.SetAncMode)?.mode
        return PendingSnapshot(pendingAncMode = ancPending, count = map.size)
    }

    // ── Optimistic update ───────────────────────────────────

    /**
     * Pure: compute the optimistic state for a command.
     * Returns null when no update applies (ANC mode — uses pendingAncMode, or setting not yet reported).
     */
    fun optimisticUpdate(baseState: AapPodState, command: AapCommand): AapPodState? {
        val updated: Pair<KClass<out AapSetting>, AapSetting> = when (command) {
            is AapCommand.SetAncMode -> return null
            is AapCommand.SetConversationalAwareness -> {
                val cur = baseState.setting<AapSetting.ConversationalAwareness>() ?: return null
                AapSetting.ConversationalAwareness::class to cur.copy(enabled = command.enabled)
            }
            is AapCommand.SetNcWithOneAirPod -> {
                val cur = baseState.setting<AapSetting.NcWithOneAirPod>() ?: return null
                AapSetting.NcWithOneAirPod::class to cur.copy(enabled = command.enabled)
            }
            is AapCommand.SetVolumeSwipe -> {
                val cur = baseState.setting<AapSetting.VolumeSwipe>() ?: return null
                AapSetting.VolumeSwipe::class to cur.copy(enabled = command.enabled)
            }
            is AapCommand.SetPersonalizedVolume -> {
                val cur = baseState.setting<AapSetting.PersonalizedVolume>() ?: return null
                AapSetting.PersonalizedVolume::class to cur.copy(enabled = command.enabled)
            }
            is AapCommand.SetToneVolume -> {
                baseState.setting<AapSetting.ToneVolume>() ?: return null
                AapSetting.ToneVolume::class to AapSetting.ToneVolume(level = command.level)
            }
            is AapCommand.SetAdaptiveAudioNoise -> {
                baseState.setting<AapSetting.AdaptiveAudioNoise>() ?: return null
                AapSetting.AdaptiveAudioNoise::class to AapSetting.AdaptiveAudioNoise(level = command.level)
            }
            is AapCommand.SetPressSpeed -> {
                baseState.setting<AapSetting.PressSpeed>() ?: return null
                AapSetting.PressSpeed::class to AapSetting.PressSpeed(value = command.value)
            }
            is AapCommand.SetPressHoldDuration -> {
                baseState.setting<AapSetting.PressHoldDuration>() ?: return null
                AapSetting.PressHoldDuration::class to AapSetting.PressHoldDuration(value = command.value)
            }
            is AapCommand.SetVolumeSwipeLength -> {
                baseState.setting<AapSetting.VolumeSwipeLength>() ?: return null
                AapSetting.VolumeSwipeLength::class to AapSetting.VolumeSwipeLength(value = command.value)
            }
            is AapCommand.SetEndCallMuteMic -> {
                baseState.setting<AapSetting.EndCallMuteMic>() ?: return null
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
                val currentInfo = baseState.deviceInfo ?: return null
                return baseState.copy(deviceInfo = currentInfo.copy(name = command.name), lastMessageAt = timeSource.now())
            }
        }
        return baseState.withSetting(updated.first, updated.second).copy(lastMessageAt = timeSource.now())
    }

    // ── Verification ────────────────────────────────────────

    /**
     * Pure: return an echo-verification check lambda for a command, or null if no verification.
     */
    fun verificationFor(command: AapCommand): ((AapPodState) -> Boolean)? = when (command) {
        is AapCommand.SetAncMode -> { s -> s.setting<AapSetting.AncMode>()?.current == command.mode }
        is AapCommand.SetAdaptiveAudioNoise -> { s -> s.setting<AapSetting.AdaptiveAudioNoise>()?.level == command.level }
        is AapCommand.SetConversationalAwareness -> { s -> s.setting<AapSetting.ConversationalAwareness>()?.enabled == command.enabled }
        is AapCommand.SetToneVolume -> { s -> s.setting<AapSetting.ToneVolume>()?.level == command.level }
        is AapCommand.SetPersonalizedVolume -> { s -> s.setting<AapSetting.PersonalizedVolume>()?.enabled == command.enabled }
        is AapCommand.SetVolumeSwipe -> { s -> s.setting<AapSetting.VolumeSwipe>()?.enabled == command.enabled }
        is AapCommand.SetNcWithOneAirPod -> { s -> s.setting<AapSetting.NcWithOneAirPod>()?.enabled == command.enabled }
        is AapCommand.SetPressSpeed -> { s -> s.setting<AapSetting.PressSpeed>()?.value == command.value }
        is AapCommand.SetPressHoldDuration -> { s -> s.setting<AapSetting.PressHoldDuration>()?.value == command.value }
        is AapCommand.SetVolumeSwipeLength -> { s -> s.setting<AapSetting.VolumeSwipeLength>()?.value == command.value }
        is AapCommand.SetEndCallMuteMic -> { s ->
            val cur = s.setting<AapSetting.EndCallMuteMic>()
            cur != null && cur.muteMic == command.muteMic && cur.endCall == command.endCall
        }
        is AapCommand.SetMicrophoneMode -> { s -> s.setting<AapSetting.MicrophoneMode>()?.mode == command.mode }
        is AapCommand.SetEarDetectionEnabled -> { s -> s.setting<AapSetting.EarDetectionEnabled>()?.enabled == command.enabled }
        is AapCommand.SetListeningModeCycle -> { s -> s.setting<AapSetting.ListeningModeCycle>()?.modeMask == command.modeMask }
        is AapCommand.SetAllowOffOption -> { s -> s.setting<AapSetting.AllowOffOption>()?.enabled == command.enabled }
        is AapCommand.SetStemConfig -> { s -> s.setting<AapSetting.StemConfig>()?.claimedPressMask == command.claimedPressMask }
        is AapCommand.SetSleepDetection -> { s -> s.setting<AapSetting.SleepDetection>()?.enabled == command.enabled }
        is AapCommand.SetDeviceName -> null
    }

    /**
     * Start a delayed verification for a sent command.
     * After 1s, checks if the device echoed the expected state.
     * On mismatch: resend once, then report [VerificationOutcome.Rejected].
     * Cancels any previous verification (single-flight).
     */
    fun startVerification(
        command: AapCommand,
        scope: CoroutineScope,
        stateProvider: () -> AapPodState,
        sendRaw: suspend (AapCommand) -> Unit,
        onOutcome: (VerificationOutcome) -> Unit,
    ) {
        val check = verificationFor(command) ?: return
        verificationJob?.cancel()
        verificationJob = scope.launch {
            delay(1000L)
            if (check(stateProvider())) {
                onOutcome(VerificationOutcome.Confirmed)
                return@launch
            }

            // Abort if pods left ear — firmware is doing its own thing
            val ear = stateProvider().setting<AapSetting.EarDetection>()
            if (ear != null && !ear.isEitherPodInEar) {
                log(TAG) { "Verification aborted for ${command::class.simpleName}: no pod in ear" }
                return@launch
            }

            log(TAG) { "Divergence detected for ${command::class.simpleName}, re-sending" }
            try {
                sendRaw(command)
            } catch (e: Exception) {
                log(TAG, ERROR) { "Verification resend failed: $e" }
                return@launch
            }

            // Second check after retry
            delay(1000L)
            if (check(stateProvider())) {
                onOutcome(VerificationOutcome.Confirmed)
            } else {
                log(TAG) { "Rejected after retry: ${command::class.simpleName}" }
                onOutcome(VerificationOutcome.Rejected(command))
            }
        }
    }

    companion object {
        private val TAG = logTag("AAP", "Coordinator")
    }
}
