package eu.darken.capod.pods.core.apple.aap.engine

import eu.darken.capod.common.TimeSource
import eu.darken.capod.pods.core.apple.aap.AapPodState
import eu.darken.capod.pods.core.apple.aap.protocol.AapCommand
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import kotlin.reflect.KClass

/**
 * Pure helper for queue ordering, optimistic state updates, and verification predicates.
 * Runtime ownership (timers, retries, pending queue state) lives in [AapSessionEngine].
 */
internal class AapSettingsCoordinator(
    private val timeSource: TimeSource,
) {

    data class PendingSnapshot(
        val pendingAncMode: AapSetting.AncMode.Value?,
        val count: Int,
    )

    data class QueueResult(
        val pendingCommands: List<AapCommand>,
        val optimisticState: AapPodState?,
        val snapshot: PendingSnapshot,
    )

    data class FlushResult(
        val commands: List<AapCommand>,
        val pendingCommands: List<AapCommand>,
        val snapshot: PendingSnapshot,
    )

    fun enqueue(
        pendingCommands: List<AapCommand>,
        command: AapCommand,
        currentState: AapPodState,
    ): QueueResult {
        var updated = pendingCommands
        if (command is AapCommand.SetAncMode && command.mode != AapSetting.AncMode.Value.ADAPTIVE) {
            updated = updated.filterNot { it is AapCommand.SetAdaptiveAudioNoise }
        }
        updated = updated.filterNot { it::class == command::class } + command

        val optimistic = if (command !is AapCommand.SetAncMode) optimisticUpdate(currentState, command) else null
        return QueueResult(
            pendingCommands = updated,
            optimisticState = optimistic,
            snapshot = snapshot(updated),
        )
    }

    fun removeFromQueue(
        pendingCommands: List<AapCommand>,
        commandClass: KClass<out AapCommand>,
    ): QueueResult {
        val updated = pendingCommands.filterNot { it::class == commandClass }
        return QueueResult(
            pendingCommands = updated,
            optimisticState = null,
            snapshot = snapshot(updated),
        )
    }

    fun flush(pendingCommands: List<AapCommand>): FlushResult {
        val sorted = pendingCommands.sortedBy {
            when (it) {
                is AapCommand.SetAllowOffOption -> 0
                is AapCommand.SetAncMode -> 1
                else -> 2
            }
        }
        return FlushResult(
            commands = sorted,
            pendingCommands = emptyList(),
            snapshot = snapshot(emptyList()),
        )
    }

    fun clear(): QueueResult = QueueResult(
        pendingCommands = emptyList(),
        optimisticState = null,
        snapshot = snapshot(emptyList()),
    )

    fun snapshot(pendingCommands: List<AapCommand>): PendingSnapshot {
        val ancPending = pendingCommands.firstOrNull { it is AapCommand.SetAncMode }
            ?.let { (it as AapCommand.SetAncMode).mode }
        return PendingSnapshot(pendingAncMode = ancPending, count = pendingCommands.size)
    }

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
                AapSetting.EndCallMuteMic::class to AapSetting.EndCallMuteMic(
                    muteMic = command.muteMic,
                    endCall = command.endCall,
                )
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
                return baseState.copy(
                    deviceInfo = currentInfo.copy(name = command.name),
                    lastMessageAt = timeSource.now(),
                )
            }
        }
        return baseState.withSetting(updated.first, updated.second).copy(lastMessageAt = timeSource.now())
    }

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
}