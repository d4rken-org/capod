package eu.darken.capod.pods.core.apple.aap

import eu.darken.capod.common.TimeSource
import eu.darken.capod.pods.core.apple.aap.protocol.AapCommand
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting

internal data class VerificationState(
    val command: AapCommand,
    val attempt: Int = 0,
)

internal data class OutboundRuntimeState(
    val pendingCommands: List<AapCommand> = emptyList(),
    val verification: VerificationState? = null,
)

internal data class OutboundDecision(
    val podState: AapPodState,
    val runtimeState: OutboundRuntimeState,
    val commandsToSend: List<AapCommand> = emptyList(),
    val timerActions: List<EngineTimerAction> = emptyList(),
    val logs: List<String> = emptyList(),
    val rejectedCommand: AapCommand? = null,
)

internal class AapOutboundController(
    timeSource: TimeSource,
) {

    private val coordinator = AapSettingsCoordinator(timeSource)

    fun onCommandRequested(
        podState: AapPodState,
        runtimeState: OutboundRuntimeState,
        command: AapCommand,
    ): OutboundDecision {
        if (command !is AapCommand.SetDeviceName) {
            val earDetection = podState.setting<AapSetting.EarDetection>()
            if (earDetection != null && !earDetection.isEitherPodInEar) {
                val result = coordinator.enqueue(runtimeState.pendingCommands, command, podState)
                return OutboundDecision(
                    podState = applyPendingSnapshot(
                        result.optimisticState ?: podState,
                        result.snapshot,
                        if (command is AapCommand.SetAncMode) command.mode else result.snapshot.pendingAncMode,
                    ),
                    runtimeState = runtimeState.copy(pendingCommands = result.pendingCommands),
                    logs = listOf("No pod in ear, queuing: ${command::class.simpleName}"),
                )
            }
        }

        var updatedPodState = podState
        var updatedRuntimeState = runtimeState
        if (command is AapCommand.SetAncMode) {
            val result = coordinator.removeFromQueue(runtimeState.pendingCommands, AapCommand.SetAncMode::class)
            updatedPodState = applyPendingSnapshot(updatedPodState, result.snapshot, command.mode)
            updatedRuntimeState = updatedRuntimeState.copy(pendingCommands = result.pendingCommands)
        }

        coordinator.optimisticUpdate(updatedPodState, command)?.let { updatedPodState = it }

        val verificationCheck = coordinator.verificationFor(command)
        return OutboundDecision(
            podState = updatedPodState,
            runtimeState = updatedRuntimeState.copy(
                verification = verificationCheck?.let { VerificationState(command = command, attempt = 0) }
                    ?: updatedRuntimeState.verification,
            ),
            commandsToSend = listOf(command),
            timerActions = if (verificationCheck != null) {
                listOf(EngineTimerAction.Start(EngineTimerKey.Verification, 1000L))
            } else {
                emptyList()
            },
        )
    }

    fun onEarDetectionInEar(
        podState: AapPodState,
        runtimeState: OutboundRuntimeState,
    ): OutboundDecision {
        val result = coordinator.flush(runtimeState.pendingCommands)
        if (result.commands.isEmpty()) return OutboundDecision(podState, runtimeState.copy(pendingCommands = result.pendingCommands))

        val ancCommand = result.commands.firstOrNull { it is AapCommand.SetAncMode } as? AapCommand.SetAncMode
        val updatedPodState = applyPendingSnapshot(
            podState,
            result.snapshot,
            ancCommand?.mode ?: podState.pendingAncMode,
        )
        val toVerify = result.commands.firstOrNull { it is AapCommand.SetAncMode } ?: result.commands.lastOrNull()
        val verificationCheck = toVerify?.let { coordinator.verificationFor(it) }
        return OutboundDecision(
            podState = updatedPodState,
            runtimeState = runtimeState.copy(
                pendingCommands = result.pendingCommands,
                verification = if (verificationCheck != null) {
                    VerificationState(command = checkNotNull(toVerify), attempt = 0)
                } else {
                    runtimeState.verification
                },
            ),
            commandsToSend = result.commands,
            timerActions = if (verificationCheck != null) {
                listOf(EngineTimerAction.Start(EngineTimerKey.Verification, 1000L))
            } else {
                emptyList()
            },
            logs = listOf("Pod in ear, flushing ${result.commands.size} queued commands"),
        )
    }

    fun onVerificationTimerFired(
        podState: AapPodState,
        runtimeState: OutboundRuntimeState,
    ): OutboundDecision {
        val verification = runtimeState.verification ?: return OutboundDecision(podState, runtimeState)
        val check = coordinator.verificationFor(verification.command)
            ?: return OutboundDecision(
                podState = podState,
                runtimeState = runtimeState.copy(verification = null),
            )

        if (check(podState)) {
            return OutboundDecision(
                podState = clearPendingForCommand(podState, verification.command),
                runtimeState = runtimeState.copy(verification = null),
            )
        }

        val ear = podState.setting<AapSetting.EarDetection>()
        if (ear != null && !ear.isEitherPodInEar) {
            return OutboundDecision(
                podState = podState,
                runtimeState = runtimeState.copy(verification = null),
                logs = listOf("Verification aborted for ${verification.command::class.simpleName}: no pod in ear"),
            )
        }

        if (verification.attempt == 0) {
            return OutboundDecision(
                podState = podState,
                runtimeState = runtimeState.copy(verification = verification.copy(attempt = 1)),
                commandsToSend = listOf(verification.command),
                timerActions = listOf(EngineTimerAction.Start(EngineTimerKey.Verification, 1000L)),
                logs = listOf("Divergence detected for ${verification.command::class.simpleName}, re-sending"),
            )
        }

        return OutboundDecision(
            podState = clearPendingForCommand(podState, verification.command),
            runtimeState = runtimeState.copy(verification = null),
            logs = listOf("Rejected after retry: ${verification.command::class.simpleName}"),
            rejectedCommand = verification.command,
        )
    }

    private fun clearPendingForCommand(
        podState: AapPodState,
        command: AapCommand,
    ): AapPodState = if (command is AapCommand.SetAncMode && podState.pendingAncMode == command.mode) {
        podState.copy(pendingAncMode = null)
    } else {
        podState
    }

    private fun applyPendingSnapshot(
        podState: AapPodState,
        snapshot: AapSettingsCoordinator.PendingSnapshot,
        ancPendingMode: AapSetting.AncMode.Value?,
    ): AapPodState = podState.copy(
        pendingAncMode = ancPendingMode,
        pendingSettingsCount = snapshot.count,
    )
}
